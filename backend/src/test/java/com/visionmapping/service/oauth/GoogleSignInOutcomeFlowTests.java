package com.visionmapping.service.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.ExternalIdentity;
import com.visionmapping.entity.enums.IdentityProvider;
import com.visionmapping.entity.enums.UserRole;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.repository.ExternalIdentityRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

/**
 * V14 phase 3 (BRD 14.0.0, GA-FR-14 to GA-FR-20, GA-FR-23 to GA-FR-25,
 * GA-SEC-06, GA-SEC-07; GAC-01 to GAC-05, GAC-07, GAC-08): the three outcomes
 * of a verified Google answer, the one-time sign-in code and linking.
 */
class GoogleSignInOutcomeFlowTests extends GoogleSignInTestSupport {

    private static final String PASSWORD = "Steady-Harbor-Lamp-42";

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private ExternalIdentityRepository identityRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private com.visionmapping.repository.UserTwoFactorRepository twoFactorRepository;

    @Autowired
    private com.visionmapping.service.twofactor.TotpSecretVault vault;

    @Test
    void gac01_gaFr16_gaFr25_aNewEmailCreatesAnAccountWithNoKnownPassword_andTheCodeWorksOnce() throws Exception {
        String sub = sub();
        String email = unique("New.Person") + "@Gmail.com";
        Map<String, String> result = fragment(googleAnswers(true, "/dreams?tab=all", sub, email, "New Person"));

        assertThat(result).containsEntry("result", "signed-in").containsEntry("remember", "true")
                .containsEntry("from", "/dreams?tab=all");
        AppUser user = appUserRepository.findByEmail(email.toLowerCase()).orElseThrow();
        assertThat(user.getFullName()).isEqualTo("New Person");
        assertThat(user.getRole()).isEqualTo("USER");
        assertThat(user.isPasswordSet()).as("GA-FR-25").isFalse();
        assertThat(user.getPasswordHash()).startsWith("$2");
        ExternalIdentity identity = identityRepository.findByProviderAndSubject(IdentityProvider.GOOGLE, sub).orElseThrow();
        assertThat(identity.getUser().getId()).isEqualTo(user.getId());
        assertThat(identity.getEmailAtLink()).isEqualTo(email);
        assertThat(events(user.getId(), "REGISTERED")).containsExactly("google");
        assertThat(jdbcTemplate.queryForObject("select code_hash from oauth_handoffs where user_id = ?", String.class, user.getId()))
                .as("only the hash of the code is kept").hasSize(64).isNotEqualTo(result.get("code"));

        JsonNode signedIn = read(exchange(result.get("code")).andExpect(status().isOk()));
        assertThat(signedIn.get("email").asText()).isEqualTo(email.toLowerCase());
        assertThat(signedIn.get("token").asText()).isNotBlank().isNotEqualTo(result.get("code"));
        assertThat(signedIn.get("refreshToken").asText()).isNotBlank();
        mockMvc.perform(get("/api/vision-areas").header("Authorization", "Bearer " + signedIn.get("token").asText()))
                .andExpect(status().isOk());
        assertThat(rememberMe(user.getId())).isTrue();
        assertThat(events(user.getId(), "GOOGLE_SIGN_IN_SUCCEEDED")).containsExactly("remember me");

        exchange(result.get("code")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(GoogleAccountService.SIGN_IN_EXPIRED));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email.toLowerCase(), "password", PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void gac02_gaFr14_aLinkedGoogleAccountSignsStraightIn_andANewGmailAddressIsRecordedOnly() throws Exception {
        String sub = sub();
        String email = unique("linked") + "@gmail.com";
        exchange(fragment(googleAnswers(true, "/", sub, email, "Linked Person")).get("code")).andExpect(status().isOk());
        Long userId = appUserRepository.findByEmail(email).orElseThrow().getId();

        String newEmail = unique("renamed") + "@gmail.com";
        Map<String, String> again = fragment(googleAnswers(false, "/tasks", sub, newEmail, "Linked Person"));
        assertThat(again).containsEntry("result", "signed-in").containsEntry("from", "/tasks");
        JsonNode signedIn = read(exchange(again.get("code")).andExpect(status().isOk()));

        assertThat(signedIn.get("email").asText()).as("the account email never follows Google").isEqualTo(email);
        ExternalIdentity identity = identityRepository.findByProviderAndSubject(IdentityProvider.GOOGLE, sub).orElseThrow();
        assertThat(identity.getLastEmail()).isEqualTo(newEmail);
        assertThat(identity.getEmailAtLink()).isEqualTo(email);
        assertThat(identity.getLastUsedAt()).isNotNull();
        assertThat(appUserRepository.findByEmail(newEmail)).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select remember_me from auth_sessions where user_id = ? order by id desc limit 1",
                Boolean.class, userId)).as("GAC-02: this tab only").isFalse();
    }

    @Test
    void gac03_gaSec06_anEmailWithAPasswordAccountNeedsThePasswordOnce_thenGoogleSignsStraightIn() throws Exception {
        String email = unique("password-owner") + "@example.com";
        register(email);
        Long userId = appUserRepository.findByEmail(email).orElseThrow().getId();
        String sub = sub();

        Map<String, String> result = fragment(googleAnswers(true, "/reviews", sub, email.toUpperCase(), "Someone"));
        assertThat(result).containsEntry("result", "link").containsEntry("from", "/reviews");
        assertThat(identityRepository.findByProviderAndSubject(IdentityProvider.GOOGLE, sub)).as("nothing is linked yet").isEmpty();
        // A link code is no sign-in code.
        exchange(result.get("code")).andExpect(status().isBadRequest());

        link(result.get("code"), "Wrong-Password-1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(GoogleAccountService.WRONG_PASSWORD));
        assertThat(identityRepository.findByProviderAndSubject(IdentityProvider.GOOGLE, sub)).isEmpty();

        JsonNode linked = read(link(result.get("code"), PASSWORD).andExpect(status().isOk()));
        assertThat(linked.get("userId").asLong()).isEqualTo(userId);
        assertThat(linked.get("token").asText()).isNotBlank();
        assertThat(identityRepository.findByProviderAndSubject(IdentityProvider.GOOGLE, sub).orElseThrow().getUser().getId())
                .isEqualTo(userId);
        assertThat(events(userId, "GOOGLE_LINKED")).containsExactly("password confirmed");
        assertThat(appUserRepository.findById(userId).orElseThrow().isPasswordSet()).isTrue();

        link(result.get("code"), PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(GoogleAccountService.LINK_EXPIRED));
        Map<String, String> next = fragment(googleAnswers(true, "/", sub, email, "Someone"));
        assertThat(next).containsEntry("result", "signed-in");
        assertThat(read(exchange(next.get("code")).andExpect(status().isOk())).get("userId").asLong()).isEqualTo(userId);
    }

    @Autowired
    private com.visionmapping.service.admin.InvitationService invitationService;

    @Test
    void umFr26_anInvitedPersonCompletesTheInvitationWithGoogleOnTheSameAddress() throws Exception {
        String email = unique("invited-google") + "@example.com";
        AppUser inviter = appUserRepository.save(AppUser.builder().fullName("Inviter").email(unique("inviter") + "@example.com")
                .passwordHash("$2a$10$x").role(UserRole.ADMIN).status(com.visionmapping.entity.enums.UserStatus.ACTIVE).build());
        AppUser invited = appUserRepository.save(AppUser.builder().fullName("Invited").email(email).passwordHash("$2a$10$x")
                .passwordSet(false).role(UserRole.USER).status(com.visionmapping.entity.enums.UserStatus.ACTIVE).build());
        invitationService.issue(invited, inviter);
        String sub = sub();

        Map<String, String> result = fragment(googleAnswers(true, "/", sub, email, "Invited"));

        assertThat(result).containsEntry("result", "signed-in");
        assertThat(identityRepository.findByProviderAndSubject(IdentityProvider.GOOGLE, sub).orElseThrow().getUser().getId())
                .isEqualTo(invited.getId());
        assertThat(events(invited.getId(), "INVITATION_ACCEPTED")).containsExactly("google");
        assertThat(jdbcTemplate.queryForObject("select count(*) from user_invitations where user_id = ? and used_at is null",
                Integer.class, invited.getId())).as("the emailed link no longer works").isZero();
        assertThat(read(exchange(result.get("code")).andExpect(status().isOk())).get("userId").asLong()).isEqualTo(invited.getId());
    }

    @Test
    void gac04_wrongPasswordsOnTheLinkingScreenCountTowardTheSignInLock() throws Exception {
        String email = unique("lock") + "@example.com";
        register(email);
        String code = fragment(googleAnswers(true, "/", sub(), email, "Lock")).get("code");
        for (int i = 0; i < 5; i++) {
            link(code, "Wrong-Password-" + i).andExpect(status().isBadRequest());
        }
        link(code, PASSWORD).andExpect(status().isTooManyRequests());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isTooManyRequests()); // the same lock as password sign-in
    }

    @Test
    void gac08_gaFr17_aDisabledAccountNeverSignsInThroughGoogle() throws Exception {
        String sub = sub();
        String email = unique("disabled") + "@gmail.com";
        String unused = fragment(googleAnswers(true, "/", sub, email, "Disabled")).get("code");
        Long userId = appUserRepository.findByEmail(email).orElseThrow().getId();
        jdbcTemplate.update("update app_users set status = 'DISABLED' where id = ?", userId);

        // A code issued before the account was disabled.
        exchange(unused).andExpect(status().isBadRequest());
        // V23 (UM-FR-32): Google proved the address, so the person is told the account is blocked.
        assertThat(googleAnswers(true, "/", sub, email, "Disabled")).isEqualTo(FRONTEND_CALLBACK + "result=error&reason=blocked");

        String passwordEmail = unique("disabled-password") + "@example.com";
        register(passwordEmail);
        jdbcTemplate.update("update app_users set status = 'DISABLED' where email = ?", passwordEmail);
        assertThat(googleAnswers(true, "/", sub(), passwordEmail, "Disabled"))
                .isEqualTo(FRONTEND_CALLBACK + "result=error&reason=blocked");
        assertThat(jdbcTemplate.queryForObject("select count(*) from security_events where event_type = 'GOOGLE_SIGN_IN_FAILED' "
                + "and detail = 'blocked'", Integer.class)).isGreaterThanOrEqualTo(2);
    }

    @Test
    void gac05_expiredOrForgedCodesAreRefused() throws Exception {
        String signInCode = fragment(googleAnswers(true, "/", sub(), unique("expired") + "@gmail.com", "Expired")).get("code");
        jdbcTemplate.update("update oauth_handoffs set expires_at = ? where code_hash = ?",
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)), OAuthValues.sha256Hex(signInCode));
        exchange(signInCode).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(GoogleAccountService.SIGN_IN_EXPIRED));

        String email = unique("expired-link") + "@example.com";
        register(email);
        String linkCode = fragment(googleAnswers(true, "/", sub(), email, "Expired")).get("code");
        jdbcTemplate.update("update oauth_handoffs set expires_at = ? where code_hash = ?",
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)), OAuthValues.sha256Hex(linkCode));
        link(linkCode, PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(GoogleAccountService.LINK_EXPIRED));

        exchange("forged-code").andExpect(status().isBadRequest());
        link("forged-code", PASSWORD).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/oauth/google/exchange").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void d14_3_gaFr26_gaFr27_aGoogleAccountSetsItsFirstPasswordWithoutACurrentOne() throws Exception {
        String email = unique("first-password") + "@gmail.com";
        String token = read(exchange(fragment(googleAnswers(false, "/", sub(), email, "First")).get("code"))
                .andExpect(status().isOk())).get("token").asText();

        JsonNode methods = read(mockMvc.perform(get("/api/account/sign-in-methods").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()));
        assertThat(methods.get("passwordSet").asBoolean()).isFalse();
        assertThat(methods.get("linkedAccounts").get(0).get("provider").asText()).isEqualTo("GOOGLE");
        assertThat(methods.get("linkedAccounts").get(0).get("email").asText()).isEqualTo(email);
        assertThat(methods.get("linkedAccounts").get(0).has("subject")).as("never the Google ID").isFalse();

        mockMvc.perform(post("/api/auth/change-password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("newPassword", PASSWORD))))
                .andExpect(status().isNoContent());
        assertThat(appUserRepository.findByEmail(email).orElseThrow().isPasswordSet()).isTrue();
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk());
        // Now that a password exists, it must be confirmed like any other account's.
        mockMvc.perform(post("/api/auth/change-password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("newPassword", "Quiet-River-Stone-77"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Current password is incorrect."));
    }

    @Test
    void v21_tfc06_withTwoFactorOnGoogleIsOnlyTheFirstFactor() throws Exception {
        String sub = sub();
        String email = unique("google-2fa") + "@gmail.com";
        exchange(fragment(googleAnswers(true, "/", sub, email, "Two Step")).get("code")).andExpect(status().isOk());
        Long userId = appUserRepository.findByEmail(email).orElseThrow().getId();
        String secret = com.visionmapping.service.twofactor.TotpCodes.newSecret();
        twoFactorRepository.saveAndFlush(com.visionmapping.entity.UserTwoFactor.builder().userId(userId)
                .method(com.visionmapping.entity.enums.TwoFactorMethod.TOTP).totpSecret(vault.seal(secret))
                .enabledAt(java.time.Instant.now()).build());

        Map<String, String> again = fragment(googleAnswers(false, "/tasks", sub, email, "Two Step"));
        JsonNode challenge = read(exchange(again.get("code")).andExpect(status().isOk()));
        assertThat(challenge.get("status").asText()).isEqualTo("TWO_FACTOR_REQUIRED");
        assertThat(challenge.has("token")).isFalse();

        String code = com.visionmapping.service.twofactor.TotpCodes.code(secret,
                com.visionmapping.service.twofactor.TotpCodes.step(java.time.Instant.now()));
        JsonNode session = read(mockMvc.perform(post("/api/auth/two-factor/verify").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("challengeToken", challenge.get("challengeToken").asText(), "code", code))))
                .andExpect(status().isOk()));
        assertThat(session.get("userId").asLong()).isEqualTo(userId);
        assertThat(events(userId, "GOOGLE_SIGN_IN_SUCCEEDED")).contains("this tab only, two-factor");
        assertThat(jdbcTemplate.queryForObject("select remember_me from auth_sessions where user_id = ? order by id desc limit 1",
                Boolean.class, userId)).isFalse();
    }

    @Test
    void gaFr16_aMissingGoogleNameFallsBackToTheEmailsFirstPart() {
        assertThat(GoogleAccountService.fullName(new GoogleIdentity("1", "ana.lim@gmail.com", " "), "ana.lim@gmail.com"))
                .isEqualTo("ana.lim");
        assertThat(GoogleAccountService.fullName(new GoogleIdentity("1", "a@gmail.com", "x".repeat(200)), "a@gmail.com"))
                .hasSize(GoogleAccountService.MAX_NAME_LENGTH);
    }

    /** Runs one full Google round trip through the stub and returns the frontend Location. */
    private String googleAnswers(boolean remember, String from, String sub, String email, String name) throws Exception {
        Map<String, String> query = start(remember, from);
        GOOGLE.answerWith(GOOGLE.sign(GoogleStubServer.claims(query.get("nonce"))
                .subject(sub).claim("email", email).claim("name", name).build()));
        String location = callback("google-code", query.get("state"));
        assertThat(location).doesNotContain("token=").doesNotContain(query.get("state"));
        return location;
    }

    private ResultActions exchange(String code) throws Exception {
        return mockMvc.perform(post("/api/auth/oauth/google/exchange").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("code", code))));
    }

    private ResultActions link(String linkToken, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/oauth/google/link").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("linkToken", linkToken, "password", password))));
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Password Owner", "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated());
    }

    private java.util.List<String> events(Long userId, String type) {
        return jdbcTemplate.queryForList("select detail from security_events where actor_id = ? and event_type = ? order by id",
                String.class, userId, type);
    }

    private boolean rememberMe(Long userId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select remember_me from auth_sessions where user_id = ? order by id desc limit 1", Boolean.class, userId));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private static String sub() {
        return "1" + Long.toUnsignedString(UUID.randomUUID().getMostSignificantBits());
    }

    private static String unique(String prefix) {
        return prefix + "-" + System.nanoTime();
    }
}
