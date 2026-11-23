package com.visionmapping.service.stepup;

import com.visionmapping.dto.request.StepUpRequest;
import com.visionmapping.dto.response.StepUpChallengeResponse;
import com.visionmapping.dto.response.StepUpStatusResponse;
import com.visionmapping.dto.response.StepUpVerifyResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.AuthSession;
import com.visionmapping.entity.StepUpChallenge;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.exception.StepUpException;
import com.visionmapping.exception.TooManyRequestsException;
import com.visionmapping.repository.AuthSessionRepository;
import com.visionmapping.repository.StepUpChallengeRepository;
import com.visionmapping.security.AppUserPrincipal;
import com.visionmapping.security.Permission;
import com.visionmapping.security.PermissionService;
import com.visionmapping.security.audit.SecurityEventService;
import com.visionmapping.security.audit.SecurityEventType;
import com.visionmapping.security.audit.SecurityOutcome;
import com.visionmapping.service.ReminderService;
import com.visionmapping.service.mail.StepUpMailer;
import com.visionmapping.service.mail.StepUpMailer.Delivery;
import com.visionmapping.service.support.EntityLookup;
import com.visionmapping.service.support.RequestRateLimiter;
import com.visionmapping.service.twofactor.AuthenticatorCodeCheck;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.mail.MailException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V20 (BRD 20.0.0, SV-FR-10 to SV-FR-19, SV-FR-32, SV-FR-33, D-20-1 to D-20-4):
 * "Verify it's you" codes.
 *
 * <ul>
 *   <li>{@link #request} sends a six-digit code by email for one action, tied to
 *       the person, their session and the action (and, for a new reminder
 *       address, that address).</li>
 *   <li>{@link #verify} checks a typed code: 10 minutes, once, 5 tries.</li>
 *   <li>{@link #require} is what a protected action calls before it changes
 *       anything: a verified, unused code for this action and session, or the
 *       session's verified window for the settings actions.</li>
 * </ul>
 *
 * Only an HMAC of the code is kept, and the code is never logged outside a
 * local or test run (SV-NFR-01). Without a mail server in production, no code
 * is sent and protected actions go on with the password checks (D-20-4), which
 * is logged.
 */
@Slf4j
@Service
public class StepUpService {

    static final Duration CODE_LIFE = Duration.ofMinutes(10);
    static final Duration WINDOW = Duration.ofMinutes(10);
    static final Duration RESEND_AFTER = Duration.ofSeconds(60);
    static final int MAX_TRIES = 5;
    static final int MAX_PER_HOUR = 5;
    static final int MAX_PER_DAY = 20;
    /** SV-FR-19: wrong tries across codes within an hour that end the person's other sessions. */
    static final int LOCKOUT_WRONG_TRIES = 10;
    static final String LOCKOUT_REASON = "STEP_UP_LOCKED";
    /** The request header that carries a verified challenge id to a protected action. */
    public static final String HEADER = "X-Step-Up";
    /** V20 SV-FR-04(b): the header that carries the code sent to a new reminder address. */
    public static final String CONFIRM_HEADER = "X-Step-Up-Confirm";
    static final String REQUIRED_MESSAGE = "Verify it's you to continue.";
    static final String ENDED_MESSAGE = "This code no longer works. Ask for a new code.";
    private static final String TARGET = "step_up";

    private final StepUpChallengeRepository repository;
    private final AuthSessionRepository sessions;
    private final StepUpMailer mailer;
    private final SecurityEventService securityEvents;
    private final RequestRateLimiter rateLimiter;
    private final EntityLookup lookup;
    private final Clock clock;
    private final boolean enabled;
    private final String secret;
    private final AuthenticatorCodeCheck authenticatorCodes;
    private final PermissionService permissions;

    public StepUpService(StepUpChallengeRepository repository, AuthSessionRepository sessions, StepUpMailer mailer,
                         SecurityEventService securityEvents, RequestRateLimiter rateLimiter, EntityLookup lookup, Clock clock,
                         @Value("${app.step-up.enabled:true}") boolean enabled,
                         @Value("${app.step-up.secret:${app.security.jwt-secret}}") String secret,
                         AuthenticatorCodeCheck authenticatorCodes, PermissionService permissions) {
        this.authenticatorCodes = authenticatorCodes;
        this.permissions = permissions;
        this.repository = repository;
        this.sessions = sessions;
        this.mailer = mailer;
        this.securityEvents = securityEvents;
        this.rateLimiter = rateLimiter;
        this.lookup = lookup;
        this.clock = clock;
        this.enabled = enabled;
        this.secret = secret;
        if (!enabled) {
            log.warn("Step-up verification is switched off (app.step-up.enabled=false): risky actions need no email code.");
        }
    }

    /** Whether protected actions currently ask for a code: switched on, and a code can reach the person. */
    public boolean active() {
        return enabled && mailer.delivery() != Delivery.NONE;
    }

    /** SV-FR-34, SV-NFR-09: for the admin warning when codes cannot be sent. */
    public StepUpStatusResponse status() {
        permissions.requireAny(lookup.currentUser(), "Only an administrator can see this.", Permission.MANAGE_COHORTS, Permission.MANAGE_AI_SETTINGS, Permission.VIEW_SECURITY_LOG);
        return new StepUpStatusResponse(enabled, mailer.delivery().name(), active());
    }

    // ---------- sending a code ----------

    /** SV-FR-10 to SV-FR-14: sends a code and answers where it went, masked. */
    @Transactional
    public StepUpChallengeResponse request(StepUpRequest request, @Nullable String language) {
        AppUser user = lookup.currentUser();
        StepUpAction action = request.action();
        String target = checkedTarget(action, request.target());
        String sendTo = target != null ? target : user.getEmail();
        if (!active()) {
            // D-20-4: no code can be sent; the page says so and the action goes on with the password checks.
            return new StepUpChallengeResponse(null, StepUpCodes.mask(sendTo), null, null, Delivery.NONE.name());
        }
        AuthSession session = currentSession();
        Instant now = clock.instant();
        List<StepUpChallenge> open = repository.findBySession_IdAndActionAndUsedAtIsNullOrderByCreatedAtDesc(session.getId(), action);
        if (!open.isEmpty()) {
            Instant allowed = open.get(0).getCreatedAt().plus(RESEND_AFTER);
            if (allowed.isAfter(now)) {
                throw new TooManyRequestsException("Wait a moment before asking for a new code.", secondsUntil(allowed, now));
            }
        }
        if (repository.countByUser_IdAndCreatedAtAfter(user.getId(), now.minus(Duration.ofHours(1))) >= MAX_PER_HOUR) {
            throw new TooManyRequestsException("You have asked for 5 codes in the last hour. Try again later.", Duration.ofHours(1).toSeconds());
        }
        if (repository.countByUser_IdAndCreatedAtAfter(user.getId(), now.minus(Duration.ofDays(1))) >= MAX_PER_DAY) {
            throw new TooManyRequestsException("You have asked for 20 codes today. Try again tomorrow.", Duration.ofDays(1).toSeconds());
        }
        // SV-FR-12: a new code cancels the earlier one.
        open.forEach(earlier -> earlier.setUsedAt(now));
        String code = StepUpCodes.newCode();
        StepUpChallenge challenge = repository.save(StepUpChallenge.builder()
                .user(user)
                .session(session)
                .action(action)
                .targetHash(target == null ? null : StepUpCodes.targetHash(target))
                .codeHash(StepUpCodes.hash(code, secret))
                .expiresAt(now.plus(CODE_LIFE))
                .createdAt(now)
                .build());
        try {
            mailer.sendCode(sendTo, code, action, "km".equals(language), CODE_LIFE.toMinutes());
        } catch (MailException exception) {
            // SV-FR-33: nothing is kept (the transaction rolls back), so it does not count against the limits.
            log.error("Could not send the verification code for {}", action, exception);
            securityEvents.record(SecurityEventType.STEP_UP_CODE_SENT, SecurityOutcome.FAILURE, TARGET, null, action + ": mail failed");
            throw new StepUpException(HttpStatus.SERVICE_UNAVAILABLE, StepUpException.MAIL_FAILED,
                    "We couldn't send the code. Try again in a few minutes.", null);
        }
        securityEvents.record(SecurityEventType.STEP_UP_CODE_SENT, SecurityOutcome.SUCCESS, TARGET, challenge.getId(), action.name());
        return new StepUpChallengeResponse(challenge.getId(), StepUpCodes.mask(sendTo), challenge.getExpiresAt(),
                now.plus(RESEND_AFTER), mailer.delivery().name());
    }

    // ---------- checking a code ----------

    /**
     * SV-FR-13, SV-FR-15, SV-FR-16, SV-FR-19: checks a typed code. A wrong try is
     * kept even though the answer is an error, so the five tries really count.
     */
    @Transactional(noRollbackFor = StepUpException.class)
    public StepUpVerifyResponse verify(Long challengeId, String typed) {
        AppUser user = lookup.currentUser();
        AuthSession session = currentSession();
        rateLimiter.check(RequestRateLimiter.Action.STEP_UP_VERIFY, "user:" + user.getId());
        Instant now = clock.instant();
        // Another person's or another session's code is "not found", as any record would be (D-20-2).
        StepUpChallenge challenge = Optional.of(repository.findByIdAndUser_IdAndSession_Id(challengeId, user.getId(), session.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Code not found. Ask for a new code.")))
                .filter(found -> found.getUsedAt() == null && found.getVerifiedAt() == null)
                .filter(found -> found.getExpiresAt().isAfter(now) && found.getAttempts() < MAX_TRIES)
                .orElseThrow(StepUpService::ended);
        String code = StepUpCodes.normalise(typed);
        boolean emailed = code != null && StepUpCodes.sameHash(StepUpCodes.hash(code, secret), challenge.getCodeHash());
        // V21 (TF-FR-40): an authenticator user may type the app's current code instead.
        if (!emailed && !authenticatorCodes.accepts(user.getId(), code)) {
            throw wrongTry(user, session, challenge, now);
        }
        challenge.setVerifiedAt(now);
        Instant windowUntil = null;
        if (challenge.getAction().rule() == StepUpAction.Rule.WINDOW) {
            windowUntil = now.plus(WINDOW);
            session.setVerifiedUntil(windowUntil);
        }
        securityEvents.record(SecurityEventType.STEP_UP_VERIFIED, SecurityOutcome.SUCCESS, TARGET, challenge.getId(),
                challenge.getAction().name());
        return new StepUpVerifyResponse(true, challenge.getId(), windowUntil);
    }

    /** Counts the wrong try and answers with the tries left. */
    private StepUpException wrongTry(AppUser user, AuthSession session, StepUpChallenge challenge, Instant now) {
        challenge.setAttempts(challenge.getAttempts() + 1);
        repository.flush();
        int left = MAX_TRIES - challenge.getAttempts();
        securityEvents.record(left == 0 ? SecurityEventType.STEP_UP_LOCKED : SecurityEventType.STEP_UP_FAILED, SecurityOutcome.DENIED,
                TARGET, challenge.getId(), challenge.getAction().name());
        // SV-FR-19: the tenth wrong try within an hour looks like someone else holding the session.
        if (repository.sumAttemptsSince(user.getId(), now.minus(Duration.ofHours(1))) == LOCKOUT_WRONG_TRIES) {
            sessions.revokeAllForUser(user.getId(), session.getId(), LOCKOUT_REASON, now);
            mailer.sendSecurityNotice(user.getEmail(), false);
        }
        String message = left == 0 ? "That code is not right. Ask for a new code."
                : "That code is not right. " + left + (left == 1 ? " try left." : " tries left.");
        return new StepUpException(HttpStatus.BAD_REQUEST, StepUpException.WRONG_CODE, message, left);
    }

    // ---------- protected actions ----------

    /**
     * SV-NFR-03: called by a protected action before it changes anything, in its
     * own transaction. Passes when step-up is not active (D-20-4, logged), when
     * a settings action falls inside the session's verified window, or when
     * challengeId names a verified, unused code for this person, session and
     * action (and target), verified within the action's minutes; that code is
     * then used up. Otherwise 403 STEP_UP_REQUIRED.
     */
    @Transactional
    public void require(StepUpAction action, @Nullable Long challengeId, @Nullable String target) {
        if (!enabled) {
            return;
        }
        if (mailer.delivery() == Delivery.NONE) {
            securityEvents.record(SecurityEventType.STEP_UP_SKIPPED_NO_MAIL, SecurityOutcome.SUCCESS, TARGET, null, action.name());
            return;
        }
        AuthSession session = currentSession();
        Instant now = clock.instant();
        if (action.rule() == StepUpAction.Rule.WINDOW && session.getVerifiedUntil() != null && session.getVerifiedUntil().isAfter(now)) {
            return;
        }
        Long userId = lookup.userId();
        String targetHash = target == null ? null : StepUpCodes.targetHash(target);
        StepUpChallenge challenge = challengeId == null ? null
                : repository.findByIdAndUser_IdAndSession_Id(challengeId, userId, session.getId())
                        .filter(found -> found.getAction() == action && found.getUsedAt() == null && found.getVerifiedAt() != null)
                        .filter(found -> found.getVerifiedAt().plus(Duration.ofMinutes(action.freshMinutes())).isAfter(now))
                        .filter(found -> !action.needsTarget() || (targetHash != null && targetHash.equals(found.getTargetHash())))
                        .orElse(null);
        if (challenge == null) {
            throw new StepUpException(HttpStatus.FORBIDDEN, StepUpException.REQUIRED, REQUIRED_MESSAGE, null, action.name());
        }
        challenge.setUsedAt(now);
    }

    /**
     * The same check with the challenge id as the page sends it in the
     * {@value #HEADER} header; a missing or malformed id is no code.
     */
    @Transactional
    public void require(StepUpAction action, @Nullable String header) {
        requireForAddress(action, header, null);
    }

    /** The same, for a code tied to an address (CONFIRM_REMINDER_EMAIL). */
    @Transactional
    public void requireForAddress(StepUpAction action, @Nullable String header, @Nullable String target) {
        Long challengeId = null;
        if (header != null && header.trim().matches("\\d{1,18}")) {
            challengeId = Long.valueOf(header.trim());
        }
        require(action, challengeId, target);
    }

    /** SV-NFR-07: codes made more than a week ago are no longer needed. */
    @Scheduled(cron = "${app.step-up.cleanup-cron:0 40 3 * * *}")
    @Transactional
    public void deleteOldChallenges() {
        int deleted = repository.deleteCreatedBefore(clock.instant().minus(Duration.ofDays(7)));
        if (deleted > 0) {
            log.info("Deleted {} old verification codes", deleted);
        }
    }

    // ---------- helpers ----------

    /** The new reminder address for CONFIRM_REMINDER_EMAIL; no address for any other action. */
    @Nullable
    private static String checkedTarget(StepUpAction action, @Nullable String target) {
        String trimmed = target == null ? "" : target.trim();
        if (!action.needsTarget()) {
            if (!trimmed.isEmpty()) {
                throw new BusinessRuleException("This action does not take an address.");
            }
            return null;
        }
        if (!ReminderService.looksLikeEmail(trimmed)) {
            throw new BusinessRuleException("That reminder email address does not look right.");
        }
        return trimmed;
    }

    private AuthSession currentSession() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long sessionId = authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal
                ? principal.getSessionId() : null;
        return (sessionId == null ? Optional.<AuthSession>empty() : sessions.findById(sessionId))
                .filter(session -> session.isActive(clock.instant()))
                .orElseThrow(() -> new StepUpException(HttpStatus.FORBIDDEN, StepUpException.REQUIRED, REQUIRED_MESSAGE, null));
    }

    private static long secondsUntil(Instant later, Instant now) {
        return Math.max(1, Duration.between(now, later).toSeconds());
    }

    private static StepUpException ended() {
        return new StepUpException(HttpStatus.GONE, StepUpException.CODE_ENDED, ENDED_MESSAGE, null);
    }
}
