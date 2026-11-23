package com.visionmapping.service.twofactor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.enums.SignInOrigin;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.repository.SignInChallengeRepository;
import com.visionmapping.repository.TwoFactorRecoveryCodeRepository;
import com.visionmapping.repository.UserTwoFactorRepository;
import com.visionmapping.security.audit.SecurityEventService;
import com.visionmapping.service.AuthSessionService;
import com.visionmapping.service.mail.StepUpMailer;
import com.visionmapping.service.notice.AccountNotices;
import com.visionmapping.service.support.LoginAttemptLimiter;
import com.visionmapping.service.support.RequestRateLimiter;
import java.time.Clock;
import org.junit.jupiter.api.Test;

/** V21 phase 2 (TF-NFR-11): with the switch off, no second step is ever asked. */
class SignInChallengeServiceTest {

    @Test
    void tfNfr11_switchedOffNoChallengeIsMade() {
        UserTwoFactorRepository settings = mock(UserTwoFactorRepository.class);
        SignInChallengeRepository challenges = mock(SignInChallengeRepository.class);
        SignInChallengeService service = new SignInChallengeService(new TwoFactorProperties(false, "secret", 5, 30, 14),
                settings, challenges, mock(TwoFactorRecoveryCodeRepository.class), mock(AppUserRepository.class),
                mock(TotpSecretVault.class), mock(TrustedDeviceService.class), mock(AuthSessionService.class),
                mock(LoginAttemptLimiter.class), mock(RequestRateLimiter.class), mock(StepUpMailer.class),
                mock(com.visionmapping.service.admin.SecurityAccessService.class),
                mock(AccountNotices.class), mock(SecurityEventService.class), Clock.systemUTC());

        assertThat(service.start(AppUser.builder().id(1L).email("a@example.com").build(), true, "/", SignInOrigin.PASSWORD, null))
                .isEmpty();
        verifyNoInteractions(settings, challenges);
    }
}
