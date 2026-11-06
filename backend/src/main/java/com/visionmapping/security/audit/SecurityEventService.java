package com.visionmapping.security.audit;

import com.visionmapping.entity.SecurityEvent;
import com.visionmapping.repository.SecurityEventRepository;
import com.visionmapping.security.AppUserPrincipal;
import com.visionmapping.util.ClientIp;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * V10 (SEC-09-01 to SEC-09-03): writes the security log.
 *
 * <p>Each event is saved in its own transaction, so it is kept even when the
 * request that caused it fails and rolls back (a refused sign-in, a denied
 * request). Writing the log never breaks a request: a failure is logged and the
 * request carries on.
 *
 * <p>The actor is the signed-in user, and the IP comes from the current
 * request. Details are written by code, never copied from user input, and are
 * cut to one line of 300 characters.
 */
@Slf4j
@Service
public class SecurityEventService {

    private static final int DETAIL_MAX = 300;

    private final SecurityEventRepository repository;
    private final SecurityAlertService alerts;
    private final Clock clock;
    private final TransactionTemplate newTransaction;
    private final Duration retention;

    public SecurityEventService(SecurityEventRepository repository, SecurityAlertService alerts, Clock clock,
                                PlatformTransactionManager transactionManager,
                                @Value("${app.security.events.retention-days:365}") long retentionDays) {
        this.repository = repository;
        this.alerts = alerts;
        this.clock = clock;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.retention = Duration.ofDays(retentionDays);
    }

    /** An event by the signed-in user, if any. */
    public void record(SecurityEventType type, SecurityOutcome outcome, String targetType, Long targetId, String detail) {
        save(type, outcome, currentUserId(), null, targetType, targetId, detail);
    }

    /** An event for a known account when nobody is signed in yet (sign-in, reset). */
    public void recordFor(Long actorId, SecurityEventType type, SecurityOutcome outcome, String detail) {
        save(type, outcome, actorId, null, null, null, detail);
    }

    /** An event for a known account, about a target record. */
    public void recordFor(Long actorId, SecurityEventType type, SecurityOutcome outcome,
                          String targetType, Long targetId, String detail) {
        save(type, outcome, actorId, null, targetType, targetId, detail);
    }

    /**
     * An event about an email address that may or may not have an account (a
     * failed sign-in, a lock, a reset request). Only a hash of the address is
     * kept, enough to count attempts against one account.
     */
    public void recordForEmail(String email, Long actorId, SecurityEventType type, SecurityOutcome outcome, String detail) {
        save(type, outcome, actorId, hashEmail(email), null, null, detail);
    }

    private void save(SecurityEventType type, SecurityOutcome outcome, Long actorId, String subjectHash,
                      String targetType, Long targetId, String detail) {
        try {
            SecurityEvent event = SecurityEvent.builder()
                    .occurredAt(clock.instant())
                    .eventType(type)
                    .outcome(outcome)
                    .actorId(actorId)
                    .subjectHash(subjectHash)
                    .ipAddress(currentIp())
                    .targetType(targetType)
                    .targetId(targetId)
                    .detail(oneLine(detail))
                    .build();
            SecurityEvent saved = newTransaction.execute(status -> repository.save(event));
            alerts.consider(saved);
        } catch (RuntimeException exception) {
            log.error("Could not write security event {} {}", type, outcome, exception);
        }
    }

    /** Daily: rows older than the retention period are removed (SEC-09-03). */
    @Scheduled(cron = "${app.security.events.cleanup-cron:0 45 3 * * *}")
    public void deleteExpired() {
        Integer deleted = newTransaction.execute(status -> repository.deleteOlderThan(clock.instant().minus(retention)));
        if (deleted != null && deleted > 0) {
            log.info("Deleted {} security events older than {} days", deleted, retention.toDays());
        }
    }

    public static String hashEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(email.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal
                ? principal.getId()
                : null;
    }

    private static String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            String ip = ClientIp.of(attributes.getRequest());
            return ip == null ? null : oneLine(ip.length() > 64 ? ip.substring(0, 64) : ip);
        }
        return null;
    }

    /** One line, so a value can never fake an extra log entry. */
    private static String oneLine(String text) {
        if (text == null) {
            return null;
        }
        String flat = text.replaceAll("[\\r\\n\\t]+", " ").trim();
        return flat.length() <= DETAIL_MAX ? flat : flat.substring(0, DETAIL_MAX - 3) + "...";
    }
}
