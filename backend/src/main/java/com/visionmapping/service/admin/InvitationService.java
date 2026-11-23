package com.visionmapping.service.admin;

import com.visionmapping.dto.response.InvitationInfoResponse;
import com.visionmapping.dto.response.SignInAnswer;
import com.visionmapping.dto.response.TwoFactorChallengeResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.UserInvitation;
import com.visionmapping.entity.enums.SignInOrigin;
import com.visionmapping.entity.enums.UserStatus;
import com.visionmapping.exception.AccountBlockedException;
import com.visionmapping.exception.InvitationGoneException;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.repository.UserInvitationRepository;
import com.visionmapping.security.audit.SecurityEventService;
import com.visionmapping.security.audit.SecurityEventType;
import com.visionmapping.security.audit.SecurityOutcome;
import com.visionmapping.service.AuthService;
import com.visionmapping.service.AuthSessionService;
import com.visionmapping.service.mail.InvitationMailer;
import com.visionmapping.service.support.PasswordPolicy;
import com.visionmapping.service.twofactor.SignInChallengeService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * V23 (UM-FR-24 to UM-FR-26, D-23-5): invitations for accounts an admin creates.
 *
 * <p>The link carries 256 random bits; only their SHA-256 is stored. Sending a new
 * invitation retires every older one, so only the latest email works. The email
 * goes out after the transaction commits, so a refused or rolled-back change
 * never mails a link that does not work.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class InvitationService {

    private static final int TOKEN_BYTES = 32;
    static final String GONE = "This invitation link was used, replaced or has expired. Ask your program admin for a new one.";

    private final UserInvitationRepository invitations;
    private final AppUserRepository users;
    private final InvitationMailer mailer;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AuthSessionService authSessionService;
    private final SignInChallengeService signInChallenges;
    private final SecurityAccessService securityAccess;
    private final SecurityEventService securityEvents;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.invitation.ttl-days:7}")
    private long ttlDays;

    @Value("${app.invitation.url:http://127.0.0.1:5173/invitation}")
    private String invitationUrl;

    /** Whether an invitation can reach anyone (UM-FR-25). */
    public boolean canDeliver() {
        return mailer.canDeliver();
    }

    /** UM-FR-24: a new link for the person, replacing any older one, emailed once this is saved. */
    public void issue(AppUser user, AppUser invitedBy) {
        Instant now = clock.instant();
        List<UserInvitation> open = invitations.findByUser_IdAndUsedAtIsNull(user.getId());
        open.forEach(invitation -> invitation.setUsedAt(now));
        invitations.saveAll(open);

        byte[] raw = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        invitations.save(UserInvitation.builder()
                .user(user)
                .tokenHash(hash(token))
                .expiresAt(now.plus(Duration.ofDays(ttlDays)))
                .createdById(invitedBy.getId())
                .createdAt(now)
                .build());
        securityEvents.record(SecurityEventType.INVITATION_SENT, SecurityOutcome.SUCCESS, "user", user.getId(),
                open.isEmpty() ? "first invitation" : "replaces an earlier invitation");

        String email = user.getEmail();
        String name = user.getFullName();
        String inviter = invitedBy.getFullName();
        String link = invitationUrl + (invitationUrl.contains("?") ? "&" : "?") + "token=" + token;
        afterCommit(() -> mailer.sendInvitation(email, name, inviter, link, ttlDays));
    }

    /** The person's latest working link, if any (the detail panel). */
    @Transactional(readOnly = true)
    public Optional<Instant> openUntil(Long userId) {
        return invitations.findFirstByUser_IdAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(userId, clock.instant())
                .map(UserInvitation::getExpiresAt);
    }

    /** Whether the person was invited and has not chosen a password yet. */
    @Transactional(readOnly = true)
    public boolean awaitingAcceptance(AppUser user) {
        return !user.isPasswordSet() && invitations.existsByUser_Id(user.getId());
    }

    /** For the invitation page: whose account the link opens, before they choose a password. */
    @Transactional(readOnly = true)
    public InvitationInfoResponse check(@Nullable String token) {
        UserInvitation invitation = open(token);
        AppUser user = invitation.getUser();
        return new InvitationInfoResponse(user.getFullName(), user.getEmail(), invitation.getExpiresAt());
    }

    /** UM-FR-24: sets the person's password, spends the link, and signs them in. */
    @Transactional(noRollbackFor = InvitationGoneException.class)
    public SignInAnswer accept(@Nullable String token, String password, boolean remember) {
        UserInvitation invitation = open(token);
        AppUser user = invitation.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            securityEvents.recordFor(user.getId(), SecurityEventType.INVITATION_ACCEPTED, SecurityOutcome.DENIED, "account blocked");
            throw new AccountBlockedException();
        }
        // V24 (SA-FR-11): with password sign-in off, the invitation is accepted with Google instead.
        securityAccess.requireAllowed(user, SignInOrigin.PASSWORD);
        passwordPolicy.check(password, user.getEmail());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setPasswordSet(true);
        users.save(user);
        invitation.setUsedAt(clock.instant());
        invitations.save(invitation);
        securityEvents.recordFor(user.getId(), SecurityEventType.INVITATION_ACCEPTED, SecurityOutcome.SUCCESS, "password chosen");

        Optional<TwoFactorChallengeResponse> challenge = signInChallenges.start(user, remember, "/", SignInOrigin.PASSWORD, null);
        if (challenge.isPresent()) {
            return challenge.get();
        }
        return AuthService.response(authSessionService.open(user, remember, SignInOrigin.PASSWORD));
    }

    /**
     * UM-FR-26: Google proved the person owns the invited address, which is what
     * the emailed link proves. Spends the open invitation; false if there is none.
     */
    public boolean acceptWithGoogle(AppUser user) {
        if (user.isPasswordSet()) {
            return false;
        }
        Optional<UserInvitation> open = invitations
                .findFirstByUser_IdAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(user.getId(), clock.instant());
        if (open.isEmpty()) {
            return false;
        }
        Instant now = clock.instant();
        invitations.findByUser_IdAndUsedAtIsNull(user.getId()).forEach(invitation -> invitation.setUsedAt(now));
        securityEvents.recordFor(user.getId(), SecurityEventType.INVITATION_ACCEPTED, SecurityOutcome.SUCCESS, "google");
        return true;
    }

    private UserInvitation open(@Nullable String token) {
        if (token == null || token.isBlank()) {
            throw new InvitationGoneException(GONE);
        }
        UserInvitation invitation = invitations.findByTokenHash(hash(token.trim())).orElse(null);
        if (invitation == null || !invitation.isOpen(clock.instant())) {
            securityEvents.recordFor(invitation == null ? null : invitation.getUser().getId(),
                    SecurityEventType.INVITATION_ACCEPTED, SecurityOutcome.DENIED,
                    invitation == null ? "unknown link" : "used or expired link");
            throw new InvitationGoneException(GONE);
        }
        return invitation;
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
