package com.visionmapping.service.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jwt.JWTClaimsSet;
import com.visionmapping.entity.OAuthLoginAttempt;
import com.visionmapping.repository.OAuthLoginAttemptRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * V14 phase 2 (BRD 14.0.0, GA-FR-05 to GA-FR-13, GA-SEC-01 to GA-SEC-05,
 * GA-SEC-08, GA-SEC-11, GAC-05, GAC-06, GAC-10): starting a Google sign-in and
 * checking Google's answer, against a local Google stub (GA-NFR-02).
 */
class GoogleSignInCallbackFlowTests extends GoogleSignInTestSupport {

    @Autowired
    private OAuthLoginAttemptRepository attemptRepository;

    @Test
    void gaFr05_to_gaFr08_startingRecordsAOneTimeAttemptAndAsksGoogleForTheBasicScopesOnly() throws Exception {
        mockMvc.perform(get("/api/auth/oauth/google/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
        Map<String, String> query = start(true, "/dreams/7?tab=goals");

        assertThat(query).containsEntry("client_id", GoogleStubServer.CLIENT_ID)
                .containsEntry("redirect_uri", "http://localhost:8080/api/auth/oauth/google/callback")
                .containsEntry("response_type", "code")
                .containsEntry("scope", "openid email profile")
                .containsEntry("code_challenge_method", "S256")
                .containsEntry("prompt", "select_account")
                .doesNotContainKey("client_secret");
        assertThat(query.get("state")).hasSize(43);
        assertThat(query.get("nonce")).hasSize(43).isNotEqualTo(query.get("state"));
        assertThat(query.get("code_challenge")).hasSize(43);

        OAuthLoginAttempt attempt = attemptRepository.findByStateHash(OAuthValues.sha256Hex(query.get("state"))).orElseThrow();
        assertThat(attempt.getStateHash()).as("only the hash of the state is kept").isNotEqualTo(query.get("state"));
        assertThat(attempt.getCodeVerifier()).as("the PKCE verifier is sealed").startsWith("v1:");
        assertThat(attempt.getNonce()).isEqualTo(query.get("nonce"));
        assertThat(attempt.isRememberMe()).isTrue();
        assertThat(attempt.getReturnPath()).isEqualTo("/dreams/7?tab=goals");
        assertThat(Duration.between(attempt.getCreatedAt(), attempt.getExpiresAt())).isEqualTo(Duration.ofMinutes(10));
        assertThat(attempt.getUsedAt()).isNull();

        Map<String, String> outside = start(false, "//evil.example/steal");
        assertThat(attemptRepository.findByStateHash(OAuthValues.sha256Hex(outside.get("state"))).orElseThrow().getReturnPath())
                .as("GA-FR-08: no open redirect").isEqualTo("/");
    }

    @Test
    void gaFr11_gaFr12_aStubSignedTokenIsVerified_withThePkceVerifierAndSecretSentToGoogle_andTheStateWorksOnce() throws Exception {
        Map<String, String> query = start(true, "/tasks");
        GOOGLE.answerWith(GOOGLE.sign(GoogleStubServer.claims(query.get("nonce"))
                .subject("110248495921238986420").claim("email", "Ana@Gmail.com").claim("name", " Ana Lim ").build()));

        GoogleSignInService.VerifiedGoogleSignIn verified = service.verifyCallback("google-code-1", query.get("state"), null);

        assertThat(verified.identity()).isEqualTo(new GoogleIdentity("110248495921238986420", "Ana@Gmail.com", "Ana Lim"));
        assertThat(verified.rememberMe()).isTrue();
        assertThat(verified.returnPath()).isEqualTo("/tasks");
        Map<String, String> sent = GOOGLE.tokenRequests().get(0);
        assertThat(sent).containsEntry("grant_type", "authorization_code").containsEntry("code", "google-code-1")
                .containsEntry("client_id", GoogleStubServer.CLIENT_ID).containsEntry("client_secret", "test-client-secret")
                .containsEntry("redirect_uri", "http://localhost:8080/api/auth/oauth/google/callback");
        assertThat(OAuthValues.pkceChallenge(sent.get("code_verifier"))).as("GA-SEC-01").isEqualTo(query.get("code_challenge"));

        assertFailure(() -> service.verifyCallback("google-code-1", query.get("state"), null), GoogleSignInFailure.EXPIRED);
        assertThat(GOOGLE.tokenRequests()).as("a used state never reaches Google").hasSize(1);
    }

    @Test
    void gaFr09_theCallbackAlwaysRedirectsToTheFrontend_withoutTokensOrReferer() throws Exception {
        Map<String, String> query = start(false, "/");
        String idToken = GOOGLE.sign(GoogleStubServer.claims(query.get("nonce")).build());
        GOOGLE.answerWith(idToken);

        String location = mockMvc.perform(get("/api/auth/oauth/google/callback")
                        .param("code", "google-code-2").param("state", query.get("state")))
                .andExpect(status().isFound())
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andReturn().getResponse().getHeader("Location");

        assertThat(location).startsWith(FRONTEND_CALLBACK).doesNotContain(idToken).doesNotContain("google-code-2")
                .doesNotContain(query.get("state"));
        assertThat(fragment(location)).containsEntry("result", "signed-in").containsKey("code");

        mockMvc.perform(get("/api/auth/oauth/google/callback").param("code", "x").param("state", "forged"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", FRONTEND_CALLBACK + "result=error&reason=expired"));
        mockMvc.perform(get("/api/auth/oauth/google/callback"))
                .andExpect(header().string("Location", FRONTEND_CALLBACK + "result=error&reason=expired"));
    }

    @Test
    void gaFr10_pressingCancelAtGoogleEndsOnTheCancelledMessage() throws Exception {
        Map<String, String> query = start(false, "/");
        mockMvc.perform(get("/api/auth/oauth/google/callback").param("error", "access_denied").param("state", query.get("state")))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", FRONTEND_CALLBACK + "result=error&reason=cancelled"));
        assertThat(GOOGLE.tokenRequests()).isEmpty();
        assertFailure(() -> service.verifyCallback("late-code", query.get("state"), null), GoogleSignInFailure.EXPIRED);
    }

    @Test
    void gaFr06_anExpiredAttemptIsRefused() throws Exception {
        Map<String, String> query = start(false, "/");
        OAuthLoginAttempt attempt = attemptRepository.findByStateHash(OAuthValues.sha256Hex(query.get("state"))).orElseThrow();
        attempt.setExpiresAt(Instant.now().minusSeconds(1));
        attemptRepository.saveAndFlush(attempt);

        assertFailure(() -> service.verifyCallback("code", query.get("state"), null), GoogleSignInFailure.EXPIRED);
        assertThat(GOOGLE.tokenRequests()).isEmpty();
    }

    @Test
    void gaFr12_gac06_everyBadIdTokenIsRefused() throws Exception {
        assertRefused(nonce -> claims(nonce).audience("someone-else.apps.googleusercontent.com"));
        assertRefused(nonce -> claims(nonce).issuer("https://evil.example"));
        assertRefused(nonce -> claims(nonce).claim("nonce", "replayed-nonce"));
        assertRefused(nonce -> claims(nonce).claim("email_verified", false));
        assertRefused(nonce -> claims(nonce).claim("email_verified", null));
        assertRefused(nonce -> claims(nonce).issueTime(Date.from(Instant.now().minus(Duration.ofHours(1))))
                .expirationTime(Date.from(Instant.now().minus(Duration.ofMinutes(3)))));
        assertRefused(nonce -> claims(nonce).expirationTime(null));
        assertRefused(nonce -> claims(nonce).claim("email", null));

        Map<String, String> query = start(false, "/");
        GOOGLE.answerWith(GoogleStubServer.signWithUnknownKey(GoogleStubServer.claims(query.get("nonce")).build(), GOOGLE.currentKeyId()));
        assertFailure(() -> service.verifyCallback("code", query.get("state"), null), GoogleSignInFailure.VERIFICATION);

        Map<String, String> garbage = start(false, "/");
        GOOGLE.answerWith("not-a-jwt");
        assertFailure(() -> service.verifyCallback("code", garbage.get("state"), null), GoogleSignInFailure.VERIFICATION);
    }

    @Test
    void gaFr12_theShortIssuerAndTwoMinutesOfClockLeewayAreAccepted() throws Exception {
        Map<String, String> query = start(false, "/");
        GOOGLE.answerWith(GOOGLE.sign(GoogleStubServer.claims(query.get("nonce")).issuer("accounts.google.com")
                .issueTime(Date.from(Instant.now().minus(Duration.ofHours(1))))
                .expirationTime(Date.from(Instant.now().minus(Duration.ofSeconds(60)))).build()));
        assertThat(service.verifyCallback("code", query.get("state"), null).identity().email()).isEqualTo("person@gmail.com");
    }

    @Test
    void gaFr12_whenGoogleRotatesItsKeysTheNewKeySetIsFetched() throws Exception {
        Map<String, String> first = start(false, "/");
        GOOGLE.answerWith(GOOGLE.sign(GoogleStubServer.claims(first.get("nonce")).build()));
        service.verifyCallback("code", first.get("state"), null);
        int fetchesBefore = GOOGLE.keySetFetches();

        GOOGLE.rotateKey();
        Map<String, String> second = start(false, "/");
        GOOGLE.answerWith(GOOGLE.sign(GoogleStubServer.claims(second.get("nonce")).build()));

        assertThat(service.verifyCallback("code", second.get("state"), null).identity().subject()).isNotBlank();
        assertThat(GOOGLE.keySetFetches()).isGreaterThan(fetchesBefore);
    }

    @Test
    void gaSec11_whenGoogleFailsOrIsSlowTheSignInFailsClosed() throws Exception {
        Map<String, String> refused = start(false, "/");
        GOOGLE.failTokenEndpoint(400);
        assertFailure(() -> service.verifyCallback("bad-code", refused.get("state"), null), GoogleSignInFailure.GOOGLE);

        GOOGLE.reset();
        Map<String, String> slow = start(false, "/");
        GOOGLE.delayTokenEndpoint(2_500);
        long started = System.nanoTime();
        assertFailure(() -> service.verifyCallback("code", slow.get("state"), null), GoogleSignInFailure.GOOGLE);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).as("gives up after the timeout").isLessThan(Duration.ofMillis(2_400));

        Map<String, String> other = start(false, "/");
        GOOGLE.reset();
        assertFailure(() -> service.verifyCallback("code", other.get("state"), "server_error"), GoogleSignInFailure.GOOGLE);
    }

    private void assertRefused(Function<String, JWTClaimsSet.Builder> claims) throws Exception {
        Map<String, String> query = start(false, "/");
        GOOGLE.answerWith(GOOGLE.sign(claims.apply(query.get("nonce")).build()));
        assertFailure(() -> service.verifyCallback("code", query.get("state"), null), GoogleSignInFailure.VERIFICATION);
    }

    private static JWTClaimsSet.Builder claims(String nonce) {
        return GoogleStubServer.claims(nonce);
    }
}
