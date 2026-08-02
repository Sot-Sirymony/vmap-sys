package com.visionmapping.service.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Delivers a password reset link by email, falling back to the log when no mail
 * server is configured.
 *
 * The branch is made at call time rather than by wiring two beans behind an
 * interface. Spring's mail auto-configuration only creates a JavaMailSender when
 * {@code spring.mail.host} is set, so the alternative was a pair of
 * {@code @ConditionalOnMissingBean} components whose selection depends on bean
 * registration order — a subtle mechanism for a decision that is one boolean.
 * Hence ObjectProvider: it tolerates the sender being absent instead of failing
 * to start.
 *
 * The fallback exists so recovery can be exercised on a laptop with no SMTP
 * server, and so a deployment that has not been given credentials still issues
 * usable tokens rather than failing at startup or silently pretending to send.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordResetMailer {

    private final ObjectProvider<JavaMailSender> mailSender;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${app.mail.from:no-reply@visionmapping.app}")
    private String from;

    public void sendResetLink(String email, String fullName, String resetLink, long ttlMinutes) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (mailHost.isBlank() || sender == null) {
            // WARN, and explicit that nothing was sent: an instance that landed
            // here by accident would otherwise look healthy while every user's
            // reset link went only to the server log.
            log.warn("""
                    No SMTP server configured (spring.mail.host is unset) — no email was sent.
                    Password reset link for {}, valid for {} minutes:
                    {}""", email, ttlMinutes, resetLink);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Reset your Vision Mapping password");
        message.setText("""
                Hello %s,

                We received a request to reset the password for your Vision Mapping account.
                Open the link below to choose a new one:

                %s

                This link expires in %d minutes and can only be used once.

                If you did not ask for this you can ignore this email. Your current
                password keeps working and nothing has been changed.
                """.formatted(fullName, resetLink, ttlMinutes));

        try {
            sender.send(message);
        } catch (MailException exception) {
            // Swallowed on purpose. The endpoint answers identically whether or
            // not the address belongs to an account, so letting a send failure
            // become a 500 would turn "this address is registered" into
            // something readable off the status code.
            log.error("Could not send the password reset email to {}", email, exception);
        }
    }
}
