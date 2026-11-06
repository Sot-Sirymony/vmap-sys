package com.visionmapping.security.audit;

import com.visionmapping.config.AsyncConfig;
import com.visionmapping.entity.SecurityEvent;
import com.visionmapping.repository.SecurityEventRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * V10 (SEC-09-04): turns patterns in the security log into alerts.
 *
 * <p>Rules, each counted over the last {@code window-minutes}:
 * <ul>
 *   <li>{@code failed-logins-per-account} failed or locked sign-ins for one email;</li>
 *   <li>{@code failed-logins-per-ip} from one network;</li>
 *   <li>{@code refusals-per-user} refused requests (403, or another user's record) by one user;</li>
 *   <li>at once: a reused refresh token (likely theft), the admin role granted,
 *       and the Claude API key changed or removed.</li>
 * </ul>
 *
 * <p>Every alert is written to the application log as a WARN line starting
 * "SECURITY ALERT", and emailed to {@code app.security.alerts.email} when that
 * and a mail server are set. The same alert is sent at most once an hour.
 * Alerts never contain an email address, password or token.
 */
@Slf4j
@Service
public class SecurityAlertService {

    private static final Set<SecurityEventType> SIGN_IN_FAILURES =
            Set.of(SecurityEventType.LOGIN_FAILED, SecurityEventType.LOGIN_LOCKED);
    private static final Duration REPEAT_AFTER = Duration.ofHours(1);
    private static final int RECENT_KEPT = 50;

    private final SecurityEventRepository repository;
    private final Clock clock;
    private final SecurityAlertService self;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final Map<String, Instant> lastSent = new ConcurrentHashMap<>();
    private final Deque<String> recent = new ArrayDeque<>();

    @Value("${app.security.alerts.window-minutes:15}")
    private long windowMinutes;
    @Value("${app.security.alerts.failed-logins-per-account:10}")
    private int failedLoginsPerAccount;
    @Value("${app.security.alerts.failed-logins-per-ip:50}")
    private int failedLoginsPerIp;
    @Value("${app.security.alerts.refusals-per-user:20}")
    private int refusalsPerUser;
    @Value("${app.security.alerts.email:}")
    private String alertEmail;
    @Value("${spring.mail.host:}")
    private String mailHost;
    @Value("${app.mail.from:no-reply@visionmapping.app}")
    private String from;

    public SecurityAlertService(SecurityEventRepository repository, Clock clock, @Lazy SecurityAlertService self,
                                ObjectProvider<JavaMailSender> mailSender) {
        this.repository = repository;
        this.clock = clock;
        this.self = self;
        this.mailSender = mailSender;
    }

    void consider(SecurityEvent event) {
        Instant since = clock.instant().minus(Duration.ofMinutes(windowMinutes));
        switch (event.getEventType()) {
            case LOGIN_FAILED, LOGIN_LOCKED -> {
                if (event.getSubjectHash() != null && repository.countByEventTypeInAndSubjectHashAndOccurredAtAfter(
                        SIGN_IN_FAILURES, event.getSubjectHash(), since) >= failedLoginsPerAccount) {
                    alert("account:" + event.getSubjectHash(), "Many failed sign-ins for one account (email hash "
                            + event.getSubjectHash().substring(0, 12) + "...) in the last " + windowMinutes + " minutes.");
                }
                if (event.getIpAddress() != null && repository.countByEventTypeInAndIpAddressAndOccurredAtAfter(
                        SIGN_IN_FAILURES, event.getIpAddress(), since) >= failedLoginsPerIp) {
                    alert("ip:" + event.getIpAddress(), "Many failed sign-ins from network " + event.getIpAddress()
                            + " in the last " + windowMinutes + " minutes.");
                }
            }
            case ACCESS_DENIED, NOT_OWNER -> {
                if (event.getActorId() != null) {
                    long refusals = repository.countByEventTypeAndActorIdAndOccurredAtAfter(SecurityEventType.ACCESS_DENIED, event.getActorId(), since)
                            + repository.countByEventTypeAndActorIdAndOccurredAtAfter(SecurityEventType.NOT_OWNER, event.getActorId(), since);
                    if (refusals >= refusalsPerUser) {
                        alert("refusals:" + event.getActorId(), "User " + event.getActorId() + " was refused "
                                + refusals + " times in the last " + windowMinutes + " minutes (probing other records?).");
                    }
                }
            }
            case REFRESH_TOKEN_REUSED -> alert("reuse:" + event.getActorId() + ":" + event.getTargetId(),
                    "A replaced refresh token was used again for user " + event.getActorId()
                            + "; the session was ended. The token may have been stolen.");
            case ROLE_CHANGED -> {
                if (event.getDetail() != null && event.getDetail().contains("ADMIN")) {
                    alert("admin:" + event.getTargetId(), "The admin role was granted to user " + event.getTargetId()
                            + " by user " + event.getActorId() + ".");
                }
            }
            case AI_KEY_CHANGED, AI_KEY_REMOVED -> alert("ai-key:" + event.getId(), "The Claude API key was "
                    + (event.getEventType() == SecurityEventType.AI_KEY_CHANGED ? "changed" : "removed")
                    + " by user " + event.getActorId() + ".");
            default -> {
                // No alert rule for this event.
            }
        }
    }

    /** The latest alerts, newest first, for tests and the admin view. */
    public synchronized List<String> recentAlerts() {
        return List.copyOf(recent);
    }

    private void alert(String key, String message) {
        Instant now = clock.instant();
        Instant previous = lastSent.get(key);
        if (previous != null && previous.plus(REPEAT_AFTER).isAfter(now)) {
            return;
        }
        if (lastSent.size() > 10_000) {
            lastSent.clear();
        }
        lastSent.put(key, now);
        synchronized (this) {
            recent.addFirst(now + " " + message);
            while (recent.size() > RECENT_KEPT) {
                recent.removeLast();
            }
        }
        log.warn("SECURITY ALERT: {}", message);
        if (!alertEmail.isBlank()) {
            self.email(message);
        }
    }

    /** Off the request thread; nothing waits on it. */
    @Async(AsyncConfig.MAIL_EXECUTOR)
    public void email(String message) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (mailHost.isBlank() || sender == null) {
            log.warn("SECURITY ALERT not emailed: no SMTP server is configured.");
            return;
        }
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(alertEmail);
        mail.setSubject("[Vision Mapping] Security alert");
        mail.setText(message + "\n\nSee the security log in Settings, or the server log, for details.");
        try {
            sender.send(mail);
        } catch (MailException exception) {
            log.error("Could not email a security alert", exception);
        }
    }
}
