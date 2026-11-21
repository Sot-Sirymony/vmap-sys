package com.visionmapping.service.notice;

import com.visionmapping.config.AsyncConfig;
import com.visionmapping.util.LogMasks;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * V20 (SV-FR-28 to SV-FR-31): the notice after a protected change. Sent only
 * after the change is committed (never for a change that failed), off the
 * request thread, with no code and no link. Without a mail server nothing is
 * sent. In English until the person's language is stored.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountNoticeMailer {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy 'at' HH:mm", Locale.ENGLISH);

    private final ObjectProvider<JavaMailSender> mailSender;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${app.mail.from:no-reply@visionmapping.app}")
    private String from;

    @Async(AsyncConfig.MAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(AccountNotice notice) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (mailHost.isBlank() || sender == null) {
            log.info("No SMTP server configured: the {} notice was not sent.", notice.kind());
            return;
        }
        for (String to : notice.recipients()) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject(notice.kind()));
            message.setText(body(notice));
            try {
                sender.send(message);
            } catch (MailException exception) {
                log.error("Could not send the {} notice to {}", notice.kind(), LogMasks.email(to), exception);
            }
        }
    }

    static String subject(AccountNotice.Kind kind) {
        return switch (kind) {
            case PASSWORD_CHANGED -> "Your Vision Map password was changed";
            case DATA_EXPORTED -> "Your Vision Map data was downloaded";
            case REMINDER_EMAIL_CHANGED -> "Your Vision Map reminder email was changed";
            case ACCOUNT_DELETED -> "Your Vision Map account was deleted";
        };
    }

    static String body(AccountNotice notice) {
        String when = WHEN.format(notice.at().atZone(zone(notice.timeZone()))) + " (" + notice.timeZone() + ")";
        String what = switch (notice.kind()) {
            case PASSWORD_CHANGED -> "The password for your Vision Map account was changed";
            case DATA_EXPORTED -> "All the data in your Vision Map account was downloaded";
            case REMINDER_EMAIL_CHANGED -> "Your Vision Map reminders now go to " + notice.detail();
            case ACCOUNT_DELETED -> "Your Vision Map account and all its data were deleted";
        };
        String advice = notice.kind() == AccountNotice.Kind.ACCOUNT_DELETED
                ? "If this was not you, contact your coach or administrator straight away."
                : "If this was not you, change your password now.";
        return """
                Hello %s,

                %s on %s, from %s.

                %s
                """.formatted(notice.fullName(), what, when, notice.browser(), advice);
    }

    private static ZoneId zone(String timeZone) {
        try {
            return ZoneId.of(timeZone);
        } catch (RuntimeException exception) {
            return ZoneId.of("UTC");
        }
    }
}
