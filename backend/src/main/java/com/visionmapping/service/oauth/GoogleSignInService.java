package com.visionmapping.service.oauth;

import com.visionmapping.dto.response.GoogleSignInStatusResponse;
import com.visionmapping.entity.OAuthLoginAttempt;
import com.visionmapping.exception.GoogleSignInUnavailableException;
import com.visionmapping.repository.OAuthHandoffRepository;
import com.visionmapping.repository.OAuthLoginAttemptRepository;
import com.visionmapping.security.audit.SecurityEventService;
import com.visionmapping.security.audit.SecurityEventType;
import com.visionmapping.security.audit.SecurityOutcome;
import com.visionmapping.exception.TooManyRequestsException;
import com.visionmapping.service.support.RequestRateLimiter;
import com.visionmapping.service.stepup.StepUpService;
import com.visionmapping.service.support.SecretCipher;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * V14 (BRD 14.0.0): Sign in with Google.
 *
 * <ul>
 *   <li>{@link #authorizeUrl} starts a sign-in: a one-time attempt with state,
 *       PKCE verifier and nonce, and Google's address (GA-FR-05 to GA-FR-08).</li>
 *   <li>{@link #callbackRedirect} handles Google's answer and always ends in a
 *       redirect to the frontend (GA-FR-09 to GA-FR-13).</li>
 * </ul>
 *
 * What a verified answer does to accounts is {@link GoogleAccountService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleSignInService {

    static final Duration ATTEMPT_LIFETIME = Duration.ofMinutes(10);
    static final String SCOPES = "openid email profile";
    static final String FRONTEND_CALLBACK_PATH = "/oauth/google/callback";

    private final GoogleOAuthSettings settings;
    private final OAuthLoginAttemptRepository attemptRepository;
    private final OAuthHandoffRepository handoffRepository;
    private final SecretCipher secretCipher;
    private final GoogleTokenClient tokenClient;
    private final GoogleIdTokenVerifier idTokenVerifier;
    private final GoogleAccountService accountService;
    private final SecurityEventService securityEvents;
    private final RequestRateLimiter rateLimiter;
    private final StepUpService stepUpService;
    private final Clock clock;

    /** GA-FR-03: whether the frontend shows the Google button. */
    public GoogleSignInStatusResponse status() {
        return new GoogleSignInStatusResponse(enabled());
    }

    /**
     * GA-FR-02 and D-14-5: on only when the Google settings are present and
     * "Verify it's you" codes can be sent. An account created with Google has no
     * password, so the emailed code is its only proof for setting a first
     * password or deleting the account; without mail it would have none.
     */
    boolean enabled() {
        return settings.enabled() && stepUpService.active();
    }

    /** D-14-5: says at start-up why Google sign-in stays off despite its settings. */
    @EventListener(ApplicationReadyEvent.class)
    public void warnWhenMailIsMissing() {
        if (settings.enabled() && !stepUpService.active()) {
            log.warn("Google sign-in stays off: GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET are set, but \"Verify it's you\" "
                    + "codes cannot be sent (set MAIL_HOST, and keep STEP_UP_ENABLED on).");
        }
    }

    /**
     * GA-FR-05 to GA-FR-08: records a sign-in attempt and returns Google's
     * sign-in address. 503 while the feature is off (GA-FR-02).
     */
    @Transactional
    public String authorizeUrl(boolean rememberMe, @Nullable String from) {
        requireEnabled();
        String state = OAuthValues.random(OAuthValues.RANDOM_BYTES);
        String nonce = OAuthValues.random(OAuthValues.RANDOM_BYTES);
        String verifier = OAuthValues.random(OAuthValues.VERIFIER_BYTES);
        Instant now = clock.instant();
        attemptRepository.save(OAuthLoginAttempt.builder()
                .stateHash(OAuthValues.sha256Hex(state))
                .codeVerifier(secretCipher.encrypt(verifier))
                .nonce(nonce)
                .rememberMe(rememberMe)
                .returnPath(OAuthValues.safeReturnPath(from))
                .expiresAt(now.plus(ATTEMPT_LIFETIME))
                .createdAt(now)
                .build());
        return UriComponentsBuilder.fromUriString(settings.authorizationUri())
                .queryParam("client_id", settings.clientId())
                .queryParam("redirect_uri", settings.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPES)
                .queryParam("state", state)
                .queryParam("nonce", nonce)
                .queryParam("code_challenge", OAuthValues.pkceChallenge(verifier))
                .queryParam("code_challenge_method", "S256")
                .queryParam("prompt", "select_account")
                .encode()
                .build()
                .toUriString();
    }

    /**
     * GA-FR-09: Google's answer, always turned into a frontend address. The
     * fragment carries only a result and a reason, never a token (GA-SEC-07).
     */
    public URI callbackRedirect(@Nullable String code, @Nullable String state, @Nullable String error,
                                @Nullable String clientIp) {
        try {
            limit(clientIp);
            VerifiedGoogleSignIn verified = verifyCallback(code, state, error);
            return finish(verified);
        } catch (GoogleSignInException exception) {
            log.info("Google sign-in did not finish ({}): {}", exception.failure().reason(), exception.getMessage());
            // GA-FR-33: the reason code only, never a token or a Google ID.
            securityEvents.record(SecurityEventType.GOOGLE_SIGN_IN_FAILED, SecurityOutcome.DENIED, null, null,
                    exception.failure().reason());
            return errorRedirect(exception.failure());
        }
    }

    /**
     * GA-FR-09 to GA-FR-13: uses the attempt once, exchanges the code with the
     * PKCE verifier and verifies the ID token. No database transaction is held
     * while Google is called.
     */
    VerifiedGoogleSignIn verifyCallback(@Nullable String code, @Nullable String state, @Nullable String error) {
        if (!enabled()) {
            throw new GoogleSignInException(GoogleSignInFailure.UNAVAILABLE, "Google sign-in is off");
        }
        OAuthLoginAttempt attempt = useAttempt(state);
        if (error != null && !error.isBlank()) {
            throw "access_denied".equals(error)
                    ? new GoogleSignInException(GoogleSignInFailure.CANCELLED, "The person cancelled at Google")
                    : new GoogleSignInException(GoogleSignInFailure.GOOGLE, "Google answered with an error");
        }
        if (code == null || code.isBlank()) {
            throw new GoogleSignInException(GoogleSignInFailure.VERIFICATION, "Google's answer had no code");
        }
        String verifier = secretCipher.decrypt(attempt.getCodeVerifier())
                .orElseThrow(() -> new GoogleSignInException(GoogleSignInFailure.EXPIRED, "The PKCE verifier cannot be read"));
        String idToken = tokenClient.exchange(code, verifier);
        GoogleIdentity identity = idTokenVerifier.verify(idToken, attempt.getNonce());
        return new VerifiedGoogleSignIn(identity, attempt.isRememberMe(), attempt.getReturnPath());
    }

    /** GA-FR-06, GA-FR-09, GA-SEC-02: unknown, expired or used states are refused. */
    private OAuthLoginAttempt useAttempt(@Nullable String state) {
        if (state == null || state.isBlank()) {
            throw new GoogleSignInException(GoogleSignInFailure.EXPIRED, "Google's answer had no state");
        }
        OAuthLoginAttempt attempt = attemptRepository.findByStateHash(OAuthValues.sha256Hex(state))
                .orElseThrow(() -> new GoogleSignInException(GoogleSignInFailure.EXPIRED, "Unknown state"));
        if (attemptRepository.markUsed(attempt.getId(), clock.instant()) != 1) {
            throw new GoogleSignInException(GoogleSignInFailure.EXPIRED, "The state is expired or already used");
        }
        return attempt;
    }

    /** GA-FR-14 to GA-FR-19: signed in, link needed, or a new account. */
    private URI finish(VerifiedGoogleSignIn verified) {
        return frontendCallback(accountService.outcome(verified));
    }

    URI errorRedirect(GoogleSignInFailure failure) {
        return frontendCallback("result=error&reason=" + failure.reason());
    }

    private URI frontendCallback(String fragment) {
        return URI.create(settings.frontendUrl() + FRONTEND_CALLBACK_PATH + "#" + fragment);
    }

    /** GA-SEC-09: the callback answers with a redirect even when over the limit. */
    private void limit(@Nullable String clientIp) {
        try {
            rateLimiter.check(RequestRateLimiter.Action.GOOGLE_SIGN_IN, clientIp);
        } catch (TooManyRequestsException exception) {
            throw new GoogleSignInException(GoogleSignInFailure.TOO_MANY, "Too many Google sign-in steps from one network");
        }
    }

    /** GA-FR-02: refuses with 503 while the Google settings are missing. */
    void requireEnabled() {
        if (!enabled()) {
            throw new GoogleSignInUnavailableException();
        }
    }

    /** GA-FR-31: daily, like old sessions; expired attempts and codes are no longer needed. */
    @Scheduled(cron = "${app.oauth.google.cleanup-cron:0 50 3 * * *}")
    @Transactional
    public void deleteExpired() {
        Instant now = clock.instant();
        int attempts = attemptRepository.deleteExpiredBefore(now);
        int handoffs = handoffRepository.deleteExpiredBefore(now);
        if (attempts + handoffs > 0) {
            log.info("Deleted {} expired Google sign-in attempts and {} expired hand-off codes", attempts, handoffs);
        }
    }

    /** A verified Google answer and what the person chose when they started (GA-FR-06). */
    record VerifiedGoogleSignIn(GoogleIdentity identity, boolean rememberMe, String returnPath) {
    }
}
