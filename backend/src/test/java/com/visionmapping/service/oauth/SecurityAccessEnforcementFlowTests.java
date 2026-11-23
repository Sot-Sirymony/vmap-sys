package com.visionmapping.service.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.visionmapping.entity.enums.SignInOrigin;
import com.visionmapping.exception.SignInMethodOffException;
import com.visionmapping.repository.AuthSessionRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

/**
 * V24 (BRD 24.0.0) phase 2: the switches are enforced at every way in
 * (SA-FR-10 to SA-FR-12), Admins are never affected (D-24-5), switching off
 * ends sessions opened with the method (D-24-3), and changes that would
 * strand people are refused (SA-FR-06). Google is available here (stub).
 *
 * <p>The shared test database always holds password-only and Google-only
 * accounts, so a real switch-off through the API is always refused there;
 * enforcement is tested with the setting written directly, and the successful
 * change in {@code SecurityAccessServiceTest}.
 */
class SecurityAccessEnforcementFlowTests extends GoogleSignInTestSupport {

    private static final String PASSWORD = "Steady-Harbor-Lamp-42";
    private static final AtomicInteger NEXT_IP = new AtomicInteger();

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AuthSessionRepository sessions;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactions;

    @AfterEach
    void restoreDefaults() {
        jdbc.update("update security_settings set password_sign_in = true, google_sign_in = true, "
                + "two_factor_scope = 'PRIVILEGED', updated_by_id = null where id = 1");
    }

    @Test
    void withPasswordOffARightPasswordIsRefusedPlainlyButWrongOnesStayGenericAndAdminsStillSignIn() throws Exception {
        String participant = unique("pw-off") + "@example.com";
        register(participant).andExpect(status().isCreated());
        String admin = admin();
        setMethods(false, true);

        login(participant, "Wrong-Password-99").andExpect(status().isUnauthorized());
        login(participant, PASSWORD).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(SignInMethodOffException.PASSWORD_OFF));
        register(unique("pw-off-new") + "@example.com").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(SignInMethodOffException.PASSWORD_SIGN_UP_OFF));
        login(admin, PASSWORD).andExpect(status().isOk());
        assertThat(jdbc.queryForList("select e.detail from security_events e join app_users u on u.id = e.actor_id "
                + "where u.email = ? and e.event_type = 'LOGIN_FAILED'", String.class, participant))
                .contains("password sign-in off");
        assertThat(read(mockMvc.perform(get("/api/auth/sign-in-methods"))).get("password").asBoolean()).isFalse();
    }

    @Test
    void withGoogleOffGoogleSignInSignUpAndLinkingAreRefusedButAdminsStillSignIn() throws Exception {
        String sub = sub();
        String googleUser = unique("google-off") + "@gmail.com";
        fragment(googleAnswers(sub, googleUser));
        String adminSub = sub();
        String googleAdmin = unique("google-admin") + "@gmail.com";
        fragment(googleAnswers(adminSub, googleAdmin));
        jdbc.update("update app_users set role = 'ADMIN' where email = ?", googleAdmin);
        String passwordOwner = unique("google-off-link") + "@example.com";
        register(passwordOwner).andExpect(status().isCreated());
        setMethods(true, false);

        String refused = FRONTEND_CALLBACK + "result=error&reason=google-off";
        assertThat(googleAnswers(sub, googleUser)).as("sign-in").isEqualTo(refused);
        assertThat(googleAnswers(sub(), unique("google-off-new") + "@gmail.com")).as("sign-up").isEqualTo(refused);
        assertThat(googleAnswers(sub(), passwordOwner)).as("linking").isEqualTo(refused);
        assertThat(fragment(googleAnswers(adminSub, googleAdmin))).containsEntry("result", "signed-in");
        assertThat(read(mockMvc.perform(get("/api/auth/sign-in-methods"))).get("google").asBoolean()).isFalse();
    }

    @Test
    void switchingAMethodOffEndsOnlyNonAdminSessionsOpenedWithItAtTheirNextRequest() throws Exception {
        String participant = unique("ended") + "@example.com";
        register(participant).andExpect(status().isCreated());
        String participantToken = token(login(participant, PASSWORD));
        String adminToken = token(login(admin(), PASSWORD));
        String googleToken = token(exchange(fragment(googleAnswers(sub(), unique("kept") + "@gmail.com")).get("code")));

        Integer ended = new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status ->
                sessions.revokeBySignInMethod(SignInOrigin.PASSWORD, "ADMIN", "sign-in method turned off", Instant.now()));

        assertThat(ended).isPositive();
        mockMvc.perform(get("/api/vision-areas").header("Authorization", "Bearer " + participantToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/vision-areas").header("Authorization", "Bearer " + adminToken)).andExpect(status().isOk());
        mockMvc.perform(get("/api/vision-areas").header("Authorization", "Bearer " + googleToken)).andExpect(status().isOk());
    }

    @Test
    void changesThatWouldStrandPeopleOrDoNothingAreRefusedAndOnlyAdminsMayChange() throws Exception {
        String admin = token(login(admin(), PASSWORD));
        register(unique("only-password") + "@example.com").andExpect(status().isCreated());
        // Password-only participants exist, so password sign-in cannot be turned off (D-24-6).
        update(admin, false, true).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("only sign in with a password")));
        // Google-only participants exist too.
        fragment(googleAnswers(sub(), unique("only-google") + "@gmail.com"));
        update(admin, true, false).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("only sign in with Google")));
        update(admin, false, false).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("At least one sign-in method must stay on."));
        update(admin, true, true).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Nothing to change."));

        String participant = unique("not-admin") + "@example.com";
        register(participant).andExpect(status().isCreated());
        update(token(login(participant, PASSWORD)), true, false).andExpect(status().isForbidden());

        Map<String, Object> noReason = new HashMap<>(Map.of("passwordSignIn", true, "googleSignIn", false));
        mockMvc.perform(put("/api/admin/security-access").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(noReason)))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select google_sign_in from security_settings where id = 1", Boolean.class)).isTrue();
    }

    private ResultActions update(String token, boolean password, boolean google) throws Exception {
        return mockMvc.perform(put("/api/admin/security-access").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of(
                        "passwordSignIn", password, "googleSignIn", google, "reason", "Testing the sign-in switches"))));
    }

    private void setMethods(boolean password, boolean google) {
        jdbc.update("update security_settings set password_sign_in = ?, google_sign_in = ? where id = 1", password, google);
    }

    private String admin() throws Exception {
        String email = unique("sa-admin") + "@example.com";
        register(email).andExpect(status().isCreated());
        jdbc.update("update app_users set role = 'ADMIN' where email = ?", email);
        return email;
    }

    private String googleAnswers(String sub, String email) throws Exception {
        Map<String, String> query = start(false, "/");
        GOOGLE.answerWith(GOOGLE.sign(GoogleStubServer.claims(query.get("nonce"))
                .subject(sub).claim("email", email).claim("name", "Switch Test").build()));
        return callback("google-code", query.get("state"));
    }

    private ResultActions exchange(String code) throws Exception {
        return mockMvc.perform(post("/api/auth/oauth/google/exchange").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("code", code))));
    }

    private ResultActions register(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("fullName", "Switch Test", "email", email, "password", PASSWORD))));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .header("X-Forwarded-For", "10.94." + (NEXT_IP.incrementAndGet() % 250) + ".1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private String token(ResultActions result) throws Exception {
        return read(result.andExpect(status().isOk())).get("token").asText();
    }

    private com.fasterxml.jackson.databind.JsonNode read(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static String sub() {
        return "1" + Long.toUnsignedString(UUID.randomUUID().getMostSignificantBits());
    }

    private static String unique(String prefix) {
        return prefix + "-" + System.nanoTime();
    }
}
