package com.visionmapping.service.mail;

import com.visionmapping.service.stepup.StepUpAction;
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
import org.springframework.stereotype.Component;

/**
 * V20 (SV-FR-17, SV-FR-18, SV-FR-32, SV-FR-33): the "Verify it's you" code by
 * email, in English or Khmer. Sent on the request thread, so a failure can be
 * shown to the person (they are signed in, so this reveals nothing about who
 * has an account). Without a mail server the code is written to the log on a
 * local or test run only (as the reset link is); anywhere else nothing is sent
 * and nothing is logged.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StepUpMailer {

    /** How a code reaches the person. NONE: no mail server outside local/test (D-20-4). */
    public enum Delivery { EMAIL, LOG, NONE }

    private final ObjectProvider<JavaMailSender> mailSender;
    private final Environment environment;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${app.mail.from:no-reply@visionmapping.app}")
    private String from;

    public Delivery delivery() {
        if (!mailHost.isBlank() && mailSender.getIfAvailable() != null) {
            return Delivery.EMAIL;
        }
        return Arrays.stream(environment.getActiveProfiles()).anyMatch(profile -> profile.equals("local") || profile.equals("test"))
                ? Delivery.LOG : Delivery.NONE;
    }

    /**
     * The code email. The code is in the subject so phones can offer it; the body
     * names the action and has no link (SV-FR-17).
     *
     * @throws MailException when the mail server refuses or times out (SV-FR-33)
     */
    public void sendCode(String to, String code, StepUpAction action, boolean khmer, long minutes) {
        Delivery delivery = delivery();
        if (delivery == Delivery.LOG) {
            log.warn("No SMTP server configured — no email was sent. Verification code for {} to {}: {} (valid {} minutes)",
                    LogMasks.email(to), action, code, minutes);
            return;
        }
        if (delivery == Delivery.NONE) {
            return;
        }
        String subject = khmer ? "លេខកូដ Vision Map របស់អ្នក៖ " + code : "Your Vision Map code: " + code;
        String body = khmer ? """
                សួស្តី

                លេខកូដរបស់អ្នក ដើម្បី%s គឺ៖

                %s

                វាដំណើរការរយៈពេល %d នាទី ហើយប្រើបានតែម្តងប៉ុណ្ណោះ។

                ប្រសិនបើមិនមែនជាអ្នកទេ កុំចែករំលែកលេខកូដនេះជាមួយនរណាម្នាក់ ហើយប្តូរពាក្យសម្ងាត់របស់អ្នកភ្លាមៗ។
                """.formatted(action.label(true), code, minutes) : """
                Hello,

                Your code to %s is:

                %s

                It works for %d minutes and only once.

                If this was not you, do not share this code with anyone and change your password now.
                """.formatted(action.label(false), code, minutes);
        send(to, subject, body);
    }

    /**
     * V21 (TF-FR-14): the second step of signing in for an email-method account.
     * Same rules as the step-up email: code in the subject, no link.
     *
     * @throws MailException when the mail server refuses or times out
     */
    public void sendSignInCode(String to, String code, long minutes) {
        Delivery delivery = delivery();
        if (delivery == Delivery.LOG) {
            log.warn("No SMTP server configured — no email was sent. Sign-in code for {}: {} (valid {} minutes)",
                    LogMasks.email(to), code, minutes);
            return;
        }
        if (delivery == Delivery.NONE) {
            return;
        }
        send(to, "Your Vision Map sign-in code: " + code, """
                Hello,

                Your code to finish signing in is:

                %s

                It works for %d minutes and only once.

                If you did not just sign in, someone knows your password. Do not share this code; change your password now.
                """.formatted(code, minutes));
    }

    /**
     * SV-FR-19: many wrong codes look like someone else holding the session.
     * Never throws: the person's other sessions have already ended.
     */
    public void sendSecurityNotice(String to, boolean khmer) {
        if (delivery() != Delivery.EMAIL) {
            log.warn("Many wrong verification codes for {}: other sessions ended; no notice email sent (no mail server).",
                    LogMasks.email(to));
            return;
        }
        String subject = khmer ? "Vision Map៖ ការព្រមានសុវត្ថិភាព" : "Vision Map: security notice";
        String body = khmer ? """
                សួស្តី

                មាននរណាម្នាក់បានវាយលេខកូដផ្ទៀងផ្ទាត់ខុសច្រើនដងនៅលើគណនីរបស់អ្នក។ យើងបានបញ្ចប់ការចូលប្រើផ្សេងទៀតរបស់អ្នកទាំងអស់។

                ប្រសិនបើមិនមែនជាអ្នកទេ សូមប្តូរពាក្យសម្ងាត់របស់អ្នកភ្លាមៗ។
                """ : """
                Hello,

                Someone typed many wrong verification codes on your account. We signed you out everywhere else.

                If this was not you, change your password now.
                """;
        try {
            send(to, subject, body);
        } catch (MailException exception) {
            log.error("Could not send the security notice to {}", LogMasks.email(to), exception);
        }
    }

    private void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.getObject().send(message);
    }
}
