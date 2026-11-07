package com.visionmapping.service.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

/** BRD 12 COV-FR-29: the password reset and reminder mailers, with the mail server mocked. */
@ExtendWith(OutputCaptureExtension.class)
class MailersTest {

    private static final String LINK = "https://vms.example/reset-password?token=secret-token-123";

    private JavaMailSender sender;
    private ObjectProvider<JavaMailSender> provider;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void mockMailServer() {
        sender = mock(JavaMailSender.class);
        provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
    }

    @Nested
    class PasswordReset {

        private PasswordResetMailer mailer(String host, String... profiles) {
            MockEnvironment environment = new MockEnvironment();
            environment.setActiveProfiles(profiles);
            PasswordResetMailer mailer = new PasswordResetMailer(provider, environment);
            ReflectionTestUtils.setField(mailer, "mailHost", host);
            ReflectionTestUtils.setField(mailer, "from", "no-reply@vms.example");
            return mailer;
        }

        @Test
        void emailsTheLinkWithItsExpiryWhenAMailServerIsSet() {
            mailer("smtp.example").sendResetLink("chan@example.com", "Sok Chan", LINK, 30);

            ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(sender).send(sent.capture());
            SimpleMailMessage message = sent.getValue();
            assertThat(message.getTo()).containsExactly("chan@example.com");
            assertThat(message.getFrom()).isEqualTo("no-reply@vms.example");
            assertThat(message.getSubject()).isEqualTo("Reset your Vision Mapping password");
            assertThat(message.getText()).contains("Hello Sok Chan,").contains(LINK).contains("expires in 30 minutes");
        }

        @Test
        void neverLetsASendFailureEscapeAndNeverLogsTheLink(CapturedOutput output) {
            doThrow(new MailSendException("Connection refused")).when(sender).send(any(SimpleMailMessage.class));

            mailer("smtp.example", "prod").sendResetLink("chan@example.com", "Sok Chan", LINK, 30);

            assertThat(output).contains("Could not send the password reset email").doesNotContain("secret-token-123");
        }

        @Test
        void logsTheLinkInsteadOfSendingOnALocalOrTestRun(CapturedOutput output) {
            mailer("", "local").sendResetLink("chan@example.com", "Sok Chan", LINK, 30);

            verify(sender, never()).send(any(SimpleMailMessage.class));
            assertThat(output).contains("no email was sent").contains(LINK).doesNotContain("chan@example.com");
        }

        @Test
        void neverLogsTheLinkOutsideLocalAndTest(CapturedOutput output) {
            mailer("", "prod").sendResetLink("chan@example.com", "Sok Chan", LINK, 30);

            verify(sender, never()).send(any(SimpleMailMessage.class));
            assertThat(output).contains("was not sent").contains("not logged outside local/test").doesNotContain("secret-token-123");
        }

        @Test
        void fallsBackToTheLogWhenTheMailSenderIsMissing(CapturedOutput output) {
            when(provider.getIfAvailable()).thenReturn(null);

            mailer("smtp.example", "test").sendResetLink("chan@example.com", "Sok Chan", LINK, 30);

            assertThat(output).contains("no email was sent");
        }
    }

    @Nested
    class Reminder {

        private ReminderMailer mailer(String host) {
            ReminderMailer mailer = new ReminderMailer(provider);
            ReflectionTestUtils.setField(mailer, "mailHost", host);
            ReflectionTestUtils.setField(mailer, "from", "reminders@vms.example");
            return mailer;
        }

        @Test
        void sendsTheReminderWhenAMailServerIsSet() {
            ReminderMailer mailer = mailer("smtp.example");
            assertThat(mailer.isConfigured()).isTrue();

            mailer.send("chan@example.com", "Today: visit five shops.");

            ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(sender).send(sent.capture());
            assertThat(sent.getValue().getSubject()).isEqualTo("Your Vision Mapping day");
            assertThat(sent.getValue().getText()).isEqualTo("Today: visit five shops.");
            assertThat(sent.getValue().getFrom()).isEqualTo("reminders@vms.example");
        }

        @Test
        void logsThatNothingWasSentButNeverTheContent(CapturedOutput output) {
            ReminderMailer mailer = mailer("");
            assertThat(mailer.isConfigured()).isFalse();

            mailer.send("chan@example.com", "Today: visit five shops.");

            verify(sender, never()).send(any(SimpleMailMessage.class));
            assertThat(output).contains("no reminder was emailed").doesNotContain("visit five shops").doesNotContain("chan@example.com");
        }

        @Test
        void swallowsASendFailure(CapturedOutput output) {
            doThrow(new MailSendException("Connection refused")).when(sender).send(any(SimpleMailMessage.class));

            mailer("smtp.example").send("chan@example.com", "Today: visit five shops.");

            assertThat(output).contains("Could not send the reminder email").doesNotContain("visit five shops");
        }
    }
}
