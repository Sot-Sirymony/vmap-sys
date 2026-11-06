package com.visionmapping.service.mail;

import com.visionmapping.util.LogMasks;
import com.visionmapping.config.AsyncConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * FR-77: delivers the daily reminder. With no mail server it logs that nothing
 * was sent, the same way PasswordResetMailer does, but never the content: a
 * user's dreams and tasks do not belong in a server log.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderMailer {

    private final ObjectProvider<JavaMailSender> mailSender;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${app.mail.from:no-reply@visionmapping.app}")
    private String from;

    public boolean isConfigured() {
        return !mailHost.isBlank();
    }

    @Async(AsyncConfig.MAIL_EXECUTOR)
    public void send(String email, String body) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (!isConfigured() || sender == null) {
            log.warn("No SMTP server configured (spring.mail.host is unset), so no reminder was emailed to {}.", LogMasks.email(email));
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Your Vision Mapping day");
        message.setText(body);
        try {
            sender.send(message);
        } catch (MailException exception) {
            log.error("Could not send the reminder email to {}", LogMasks.email(email), exception);
        }
    }
}
