package com.visionmapping.service.mail;

import com.visionmapping.config.AsyncConfig;
import com.visionmapping.util.LogMasks;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * V23 (UM-FR-24, UM-FR-25): emails the link that lets a person an admin added
 * choose their own password.
 *
 * <p>Unlike a password reset, a missing mail server is not something to work
 * around quietly: an invitation nobody receives leaves an account nobody can
 * open. So {@link #canDeliver()} lets the caller refuse first (Q-05). On a local
 * or test run, where there is usually no mail server, the link goes to the log
 * instead so the flow can be tried.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvitationMailer {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final Environment environment;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${app.mail.from:no-reply@visionmapping.app}")
    private String from;

    /** Whether an invitation will reach anyone: a mail server, or a local/test run that logs the link. */
    public boolean canDeliver() {
        return mailConfigured() || linkMayBeLogged();
    }

    /** Runs off the request thread; failures are logged, since nobody waits on the result. */
    @Async(AsyncConfig.MAIL_EXECUTOR)
    public void sendInvitation(String email, String fullName, String invitedBy, String link, long days) {
        if (!mailConfigured()) {
            if (linkMayBeLogged()) {
                log.warn("""
                        No SMTP server configured (spring.mail.host is unset): no email was sent.
                        Invitation link for {}, valid for {} days:
                        {}""", LogMasks.email(email), days, link);
            } else {
                log.warn("No SMTP server configured: the invitation for {} was not sent.", LogMasks.email(email));
            }
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("You are invited to Vision Map");
        message.setText("""
                Hello %s,

                %s has made a Vision Map account for you. Open the link below to
                choose your password and sign in:

                %s

                This link works once and expires in %d days. If it expires, ask your
                program admin to send a new one.

                If you did not expect this email, you can ignore it. Nothing happens
                until the link is used.
                """.formatted(fullName, invitedBy, link, days));
        try {
            mailSender.getObject().send(message);
        } catch (MailException exception) {
            log.error("Could not send the invitation email to {}", LogMasks.email(email), exception);
        }
    }

    private boolean mailConfigured() {
        return !mailHost.isBlank() && mailSender.getIfAvailable() != null;
    }

    private boolean linkMayBeLogged() {
        return Arrays.stream(environment.getActiveProfiles()).anyMatch(profile -> profile.equals("local") || profile.equals("test"));
    }
}
