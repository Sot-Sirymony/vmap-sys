package com.visionmapping.service.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.visionmapping.exception.GoogleSignInUnavailableException;
import com.visionmapping.exception.TooManyRequestsException;
import com.visionmapping.repository.OAuthHandoffRepository;
import com.visionmapping.repository.OAuthLoginAttemptRepository;
import com.visionmapping.security.audit.SecurityEventService;
import com.visionmapping.security.audit.SecurityEventType;
import com.visionmapping.security.audit.SecurityOutcome;
import com.visionmapping.service.stepup.StepUpService;
import com.visionmapping.service.support.RequestRateLimiter;
import com.visionmapping.service.support.SecretCipher;
import java.time.Clock;
import org.junit.jupiter.api.Test;

/** V14 phase 5 (GA-SEC-09): over the limit, Google's callback still ends in a redirect, never JSON. */
class GoogleSignInServiceTest {

    @Test
    void gaSec09_overTheLimitTheCallbackRedirectsWithTooMany_andNothingElseRuns() {
        GoogleOAuthSettings settings = new GoogleOAuthSettings("id", "secret", "http://localhost:8080/cb", "https://app.example.test");
        OAuthLoginAttemptRepository attempts = mock(OAuthLoginAttemptRepository.class);
        SecurityEventService events = mock(SecurityEventService.class);
        RequestRateLimiter limiter = mock(RequestRateLimiter.class);
        doThrow(new TooManyRequestsException("Too many", 60L)).when(limiter).check(eq(RequestRateLimiter.Action.GOOGLE_SIGN_IN), any());
        StepUpService stepUp = mock(StepUpService.class);
        when(stepUp.active()).thenReturn(true);
        GoogleSignInService service = service(settings, attempts, events, limiter, stepUp);

        assertThat(service.callbackRedirect("code", "state", null, "203.0.113.9"))
                .hasToString("https://app.example.test/oauth/google/callback#result=error&reason=too-many");
        verifyNoInteractions(attempts);
        verify(events).record(SecurityEventType.GOOGLE_SIGN_IN_FAILED, SecurityOutcome.DENIED, null, null, "too-many");
    }

    @Test
    void d14_5_withoutWorkingEmailCodesGoogleSignInStaysOff_evenWithItsSettings() {
        GoogleOAuthSettings settings = new GoogleOAuthSettings("id", "secret", "http://localhost:8080/cb", "https://app.example.test");
        StepUpService stepUp = mock(StepUpService.class);
        when(stepUp.active()).thenReturn(false);
        GoogleSignInService service = service(settings, mock(OAuthLoginAttemptRepository.class), mock(SecurityEventService.class),
                mock(RequestRateLimiter.class), stepUp);

        assertThat(service.status().enabled()).isFalse();
        assertThatThrownBy(() -> service.authorizeUrl(true, "/")).isInstanceOf(GoogleSignInUnavailableException.class);
        assertThat(service.callbackRedirect("code", "state", null, "203.0.113.9"))
                .hasToString("https://app.example.test/oauth/google/callback#result=error&reason=unavailable");
        service.warnWhenMailIsMissing();

        when(stepUp.active()).thenReturn(true);
        assertThat(service.status().enabled()).isTrue();
    }

    private static GoogleSignInService service(GoogleOAuthSettings settings, OAuthLoginAttemptRepository attempts,
                                               SecurityEventService events, RequestRateLimiter limiter, StepUpService stepUp) {
        return new GoogleSignInService(settings, attempts, mock(OAuthHandoffRepository.class),
                new SecretCipher("", "a-test-jwt-secret-long-enough-for-tests"), mock(GoogleTokenClient.class),
                mock(GoogleIdTokenVerifier.class), mock(GoogleAccountService.class), events, limiter, stepUp, Clock.systemUTC());
    }
}
