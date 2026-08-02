package com.visionmapping.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.visionmapping.dto.request.ForgotPasswordRequest;
import com.visionmapping.dto.request.ResetPasswordRequest;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.PasswordResetToken;
import com.visionmapping.entity.enums.UserRole;
import com.visionmapping.entity.enums.UserStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.repository.PasswordResetTokenRepository;
import com.visionmapping.service.mail.PasswordResetMailer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Password recovery. A real BCryptPasswordEncoder is used because the assertions
 * that matter are about whether a password actually works afterwards, which a
 * stubbed encoder could not answer.
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final String EMAIL = "pat@example.com";
    private static final String OLD_PASSWORD = "OriginalPass123";
    private static final String NEW_PASSWORD = "ReplacementPass456";
    private static final long TTL_MINUTES = 30;

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private PasswordResetTokenRepository tokenRepository;
    @Mock
    private PasswordResetMailer mailer;

    private PasswordEncoder passwordEncoder;
    private PasswordResetService service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        service = new PasswordResetService(appUserRepository, tokenRepository, passwordEncoder, mailer);
        ReflectionTestUtils.setField(service, "ttlMinutes", TTL_MINUTES);
        ReflectionTestUtils.setField(service, "resetUrl", "https://app.example.com/reset-password");
        user = AppUser.builder()
                .id(3L)
                .fullName("Pat Owner")
                .email(EMAIL)
                .passwordHash(passwordEncoder.encode(OLD_PASSWORD))
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
    }

    // ---------- requesting a link ----------

    @Test
    @DisplayName("emails a link when the address has an account")
    void sendsLink() {
        when(appUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(tokenRepository.findByUserAndUsedAtIsNull(user)).thenReturn(List.of());

        service.requestReset(new ForgotPasswordRequest(EMAIL));

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(mailer).sendResetLink(eqEmail(), anyString(), link.capture(), anyLong());
        assertThat(link.getValue()).startsWith("https://app.example.com/reset-password?token=");
        verify(tokenRepository).save(any(PasswordResetToken.class));
    }

    /**
     * The membership-oracle rule. An unknown address must be indistinguishable
     * from a known one, or the endpoint becomes a way to enumerate which
     * addresses have accounts.
     */
    @Test
    @DisplayName("does nothing observable for an address with no account")
    void unknownAddressIsSilent() {
        when(appUserRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        service.requestReset(new ForgotPasswordRequest("nobody@example.com"));

        verifyNoInteractions(mailer);
        verify(tokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("normalises the address before looking it up")
    void normalisesEmail() {
        when(appUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(tokenRepository.findByUserAndUsedAtIsNull(user)).thenReturn(List.of());

        service.requestReset(new ForgotPasswordRequest("  PAT@Example.COM  "));

        verify(appUserRepository).findByEmail(EMAIL);
    }

    /**
     * Asking twice must not leave two working links alive in two inboxes.
     */
    @Test
    @DisplayName("retires any link the user already had outstanding")
    void supersedesOutstandingTokens() {
        PasswordResetToken previous = PasswordResetToken.builder()
                .user(user)
                .tokenHash("whatever")
                .expiresAt(Instant.now().plus(Duration.ofMinutes(TTL_MINUTES)))
                .build();
        when(appUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(tokenRepository.findByUserAndUsedAtIsNull(user)).thenReturn(List.of(previous));

        service.requestReset(new ForgotPasswordRequest(EMAIL));

        assertThat(previous.getUsedAt()).isNotNull();
        verify(tokenRepository).saveAll(List.of(previous));
    }

    /**
     * A stored token must be useless to anyone who reads the table — the same
     * reason passwords are not stored either.
     */
    @Test
    @DisplayName("stores only a hash, never the emailed token")
    void storesHashNotToken() {
        when(appUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(tokenRepository.findByUserAndUsedAtIsNull(user)).thenReturn(List.of());

        service.requestReset(new ForgotPasswordRequest(EMAIL));

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(mailer).sendResetLink(anyString(), anyString(), link.capture(), anyLong());
        String emailedToken = link.getValue().substring(link.getValue().indexOf("token=") + 6);

        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(saved.capture());
        assertThat(saved.getValue().getTokenHash())
                .isNotEqualTo(emailedToken)
                .hasSize(64); // SHA-256 as hex
    }

    // ---------- redeeming a link ----------

    @Test
    @DisplayName("sets the new password and spends the token")
    void resetsPassword() {
        PasswordResetToken token = storedTokenFor("the-token", Instant.now().plus(Duration.ofMinutes(10)));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        service.resetPassword(new ResetPasswordRequest("the-token", NEW_PASSWORD));

        assertThat(passwordEncoder.matches(NEW_PASSWORD, user.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(OLD_PASSWORD, user.getPasswordHash())).isFalse();
        assertThat(token.getUsedAt()).isNotNull();
        verify(appUserRepository).save(user);
    }

    @Test
    @DisplayName("refuses a token that does not exist")
    void rejectsUnknownToken() {
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword(new ResetPasswordRequest("made-up", NEW_PASSWORD)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not valid");
        verify(appUserRepository, never()).save(any());
    }

    /** Replay: the link is in a mailbox forever, so spending it must be final. */
    @Test
    @DisplayName("refuses a token that has already been used")
    void rejectsReplayedToken() {
        PasswordResetToken token = storedTokenFor("the-token", Instant.now().plus(Duration.ofMinutes(10)));
        token.setUsedAt(Instant.now().minusSeconds(60));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.resetPassword(new ResetPasswordRequest("the-token", NEW_PASSWORD)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already been used");
        assertThat(passwordEncoder.matches(OLD_PASSWORD, user.getPasswordHash())).isTrue();
        verify(appUserRepository, never()).save(any());
    }

    @Test
    @DisplayName("refuses a token past its expiry")
    void rejectsExpiredToken() {
        PasswordResetToken token = storedTokenFor("the-token", Instant.now().minusSeconds(1));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.resetPassword(new ResetPasswordRequest("the-token", NEW_PASSWORD)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("expired");
        assertThat(passwordEncoder.matches(OLD_PASSWORD, user.getPasswordHash())).isTrue();
        verify(appUserRepository, never()).save(any());
    }

    /**
     * The account comes from the token, not from anything the caller says, so a
     * reset cannot be pointed at somebody else's account.
     */
    @Test
    @DisplayName("changes the password of the token's owner")
    void resetsTheTokenOwner() {
        PasswordResetToken token = storedTokenFor("the-token", Instant.now().plus(Duration.ofMinutes(10)));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        service.resetPassword(new ResetPasswordRequest("the-token", NEW_PASSWORD));

        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(user.getId());
    }

    private PasswordResetToken storedTokenFor(String plainToken, Instant expiresAt) {
        return PasswordResetToken.builder()
                .user(user)
                .tokenHash(plainToken)
                .expiresAt(expiresAt)
                .build();
    }

    private String eqEmail() {
        return org.mockito.ArgumentMatchers.eq(EMAIL);
    }
}
