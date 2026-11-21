package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.ExternalIdentity;
import com.visionmapping.entity.OAuthHandoff;
import com.visionmapping.entity.OAuthLoginAttempt;
import com.visionmapping.entity.enums.IdentityProvider;
import com.visionmapping.entity.enums.OAuthHandoffKind;
import com.visionmapping.exception.GoogleSignInUnavailableException;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.repository.ExternalIdentityRepository;
import com.visionmapping.repository.OAuthHandoffRepository;
import com.visionmapping.repository.OAuthLoginAttemptRepository;
import com.visionmapping.service.oauth.GoogleSignInService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * V14 phase 1 (BRD 14.0.0): with no Google settings the feature is off and
 * answers 503 (GA-FR-02, GAC-09); the new tables keep their rules (GA-FR-28 to
 * GA-FR-30); expired attempts and codes are cleaned up (GA-FR-31).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GoogleSignInSetupFlowTests {

    private static final String PASSWORD = "Steady-Harbor-Lamp-42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GoogleSignInService googleSignInService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private ExternalIdentityRepository identityRepository;

    @Autowired
    private OAuthLoginAttemptRepository attemptRepository;

    @Autowired
    private OAuthHandoffRepository handoffRepository;

    @Autowired
    private Clock clock;

    @Test
    void gaFr02_gaFr03_withoutSettingsTheFeatureIsOffAndAnswers503_andPasswordSignInStillWorks() throws Exception {
        mockMvc.perform(get("/api/auth/oauth/google/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        mockMvc.perform(get("/api/auth/oauth/google/authorize-url").param("remember", "true").param("from", "/dreams"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(GoogleSignInUnavailableException.MESSAGE));

        String email = email("google-off");
        register(email);
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk());
    }

    @Test
    void gaFr28_oneGoogleAccountMapsToOneAppAccount() throws Exception {
        AppUser first = user("google-link-a");
        AppUser second = user("google-link-b");
        String subject = UUID.randomUUID().toString();
        identityRepository.saveAndFlush(identity(first, subject));

        assertThat(identityRepository.findByProviderAndSubject(IdentityProvider.GOOGLE, subject))
                .get().extracting(found -> found.getUser().getId()).isEqualTo(first.getId());
        assertThatThrownBy(() -> identityRepository.saveAndFlush(identity(second, subject)))
                .as("the same Google sub on a second account").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> identityRepository.saveAndFlush(identity(first, UUID.randomUUID().toString())))
                .as("a second Google link on one account").isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void gaFr31_expiredAttemptsAndHandoffsAreDeletedDaily_openOnesStay() throws Exception {
        AppUser user = user("google-cleanup");
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        OAuthLoginAttempt expiredAttempt = attemptRepository.save(attempt(now.minus(Duration.ofMinutes(1))));
        OAuthLoginAttempt openAttempt = attemptRepository.save(attempt(now.plus(Duration.ofMinutes(10))));
        OAuthHandoff expiredHandoff = handoffRepository.save(handoff(user, now.minus(Duration.ofSeconds(1))));
        OAuthHandoff openHandoff = handoffRepository.save(handoff(user, now.plus(Duration.ofSeconds(60))));

        googleSignInService.deleteExpired();

        assertThat(attemptRepository.findById(expiredAttempt.getId())).isEmpty();
        assertThat(attemptRepository.findById(openAttempt.getId())).isPresent();
        assertThat(handoffRepository.findById(expiredHandoff.getId())).isEmpty();
        assertThat(handoffRepository.findById(openHandoff.getId())).isPresent();
        assertThat(attemptRepository.findByStateHash(openAttempt.getStateHash())).isPresent();
        assertThat(handoffRepository.findByCodeHashAndKind(openHandoff.getCodeHash(), OAuthHandoffKind.SIGN_IN)).isPresent();
        assertThat(handoffRepository.findByCodeHashAndKind(openHandoff.getCodeHash(), OAuthHandoffKind.LINK)).isEmpty();
    }

    private ExternalIdentity identity(AppUser user, String subject) {
        Instant now = clock.instant();
        return ExternalIdentity.builder().user(user).provider(IdentityProvider.GOOGLE).subject(subject)
                .emailAtLink(user.getEmail()).lastEmail(user.getEmail()).linkedAt(now).build();
    }

    private OAuthLoginAttempt attempt(Instant expiresAt) {
        return OAuthLoginAttempt.builder().stateHash(randomHash()).codeVerifier("v1:sealed").nonce(UUID.randomUUID().toString())
                .rememberMe(true).returnPath("/").expiresAt(expiresAt).createdAt(clock.instant()).build();
    }

    private OAuthHandoff handoff(AppUser user, Instant expiresAt) {
        return OAuthHandoff.builder().codeHash(randomHash()).kind(OAuthHandoffKind.SIGN_IN).user(user)
                .providerSubject(UUID.randomUUID().toString()).rememberMe(false).returnPath("/")
                .expiresAt(expiresAt).createdAt(clock.instant()).build();
    }

    private static String randomHash() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
    }

    private AppUser user(String prefix) throws Exception {
        String email = email(prefix);
        register(email);
        return appUserRepository.findByEmail(email).orElseThrow();
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Google Setup", "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated());
    }

    private static String email(String prefix) {
        return prefix + "-" + System.nanoTime() + "@example.com";
    }
}
