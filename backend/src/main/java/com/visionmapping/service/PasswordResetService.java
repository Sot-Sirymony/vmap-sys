package com.visionmapping.service;

import com.visionmapping.dto.request.ForgotPasswordRequest;
import com.visionmapping.dto.request.ResetPasswordRequest;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.PasswordResetToken;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.repository.PasswordResetTokenRepository;
import com.visionmapping.service.mail.PasswordResetMailer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Password recovery by email: prove control of the inbox, then set a new
 * password without knowing the old one.
 *
 * Two properties drive most of the design.
 *
 * <b>Requesting a reset never reveals whether an address has an account.</b>
 * {@link #requestReset} returns the same way for a registered address and an
 * unknown one. An endpoint that answered differently would be a membership
 * oracle: anyone could submit addresses and learn which belong to users, which
 * is exactly the list worth having before attacking passwords.
 *
 * <b>The emailed token is never stored.</b> Only its SHA-256 is, so read access
 * to the table does not let anyone reset an account — the same reasoning that
 * keeps plaintext passwords out of app_users. SHA-256 rather than BCrypt because
 * the token is 32 bytes from SecureRandom: there is no low-entropy secret to
 * slow a guessing attack against, and lookup must be by exact hash.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PasswordResetService {

    /** 256 bits from SecureRandom — not guessable, so the hash need not be slow. */
    private static final int TOKEN_BYTES = 32;

    private final AppUserRepository appUserRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetMailer mailer;
    private final SecureRandom secureRandom = new SecureRandom();

    /** Long enough to find the mail, short enough that a leaked inbox ages out. */
    @Value("${app.password-reset.ttl-minutes:30}")
    private long ttlMinutes;

    /** Where the emailed link points — the frontend, not this API. */
    @Value("${app.password-reset.reset-url:http://127.0.0.1:5173/reset-password}")
    private String resetUrl;

    /**
     * Issues a reset link if the address belongs to an account, and does nothing
     * observable if it does not.
     *
     * <p>Returns void rather than any indication of what happened. The caller
     * cannot report more than it knows, which is the point.
     */
    public void requestReset(ForgotPasswordRequest request) {
        String email = request.email().trim().toLowerCase();
        Optional<AppUser> found = appUserRepository.findByEmail(email);

        if (found.isEmpty()) {
            // Logged, not answered: an operator can see the attempt, the caller
            // cannot tell this apart from a successful send.
            log.info("Password reset requested for an address with no account");
            return;
        }

        AppUser user = found.get();

        // Asking twice must not leave two working links alive. The older one is
        // retired so the most recent mail is the only one that opens the account.
        List<PasswordResetToken> outstanding = tokenRepository.findByUserAndUsedAtIsNull(user);
        Instant now = Instant.now();
        outstanding.forEach(token -> token.setUsedAt(now));
        tokenRepository.saveAll(outstanding);

        byte[] raw = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        tokenRepository.save(PasswordResetToken.builder()
                .user(user)
                .tokenHash(hash(token))
                .expiresAt(now.plus(Duration.ofMinutes(ttlMinutes)))
                .build());

        mailer.sendResetLink(user.getEmail(), user.getFullName(), buildLink(token), ttlMinutes);
    }

    /**
     * Redeems a link and sets the new password.
     *
     * <p>Unlike {@link #requestReset} this one does report failure: the user is
     * holding a link they believe is valid, and "expired" versus "already used"
     * is the difference between requesting a new one and realising the reset
     * already happened. The token is unguessable, so saying which is wrong tells
     * an attacker nothing they could act on.
     */
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken token = tokenRepository.findByTokenHash(hash(request.token()))
                .orElseThrow(() -> new BusinessRuleException("This reset link is not valid. Request a new one."));

        if (token.getUsedAt() != null) {
            throw new BusinessRuleException("This reset link has already been used. Request a new one.");
        }
        if (!token.getExpiresAt().isAfter(Instant.now())) {
            throw new BusinessRuleException("This reset link has expired. Request a new one.");
        }

        AppUser user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        appUserRepository.save(user);

        // Spent before returning, so the same link cannot be replayed — including
        // by anyone who later reads it out of the mailbox.
        token.setUsedAt(Instant.now());
        tokenRepository.save(token);
    }

    private String buildLink(String token) {
        String separator = resetUrl.contains("?") ? "&" : "?";
        return resetUrl + separator + "token=" + token;
    }

    /**
     * SHA-256 of the emailed token. Deterministic on purpose: redemption has to
     * find the row by hash, which a salted scheme could not do.
     */
    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            // Every JVM ships SHA-256; if it is missing, failing loudly beats
            // falling back to something weaker.
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
