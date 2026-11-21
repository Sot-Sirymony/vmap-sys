package com.visionmapping.service.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.visionmapping.service.mail.StepUpMailer.Delivery;
import com.visionmapping.service.stepup.StepUpAction;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

/** V20 SV-FR-17, SV-FR-18, SV-FR-32: the code email and how it is delivered. */
class StepUpMailerTest {

    private final JavaMailSender sender = mock(JavaMailSender.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
    private final Environment environment = mock(Environment.class);

    private StepUpMailer mailer(String host, String... profiles) {
        when(provider.getIfAvailable()).thenReturn(sender);
        when(provider.getObject()).thenReturn(sender);
        when(environment.getActiveProfiles()).thenReturn(profiles);
        StepUpMailer mailer = new StepUpMailer(provider, environment);
        ReflectionTestUtils.setField(mailer, "mailHost", host);
        ReflectionTestUtils.setField(mailer, "from", "no-reply@example.com");
        return mailer;
    }

    @Test
    void theCodeIsInTheSubjectTheBodyNamesTheActionAndHasNoLink() {
        mailer("smtp.example.com").sendCode("me@example.com", "042917", StepUpAction.EXPORT_DATA, false, 10);
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(sent.capture());
        SimpleMailMessage message = sent.getValue();
        assertThat(message.getTo()).containsExactly("me@example.com");
        assertThat(message.getFrom()).isEqualTo("no-reply@example.com");
        assertThat(message.getSubject()).isEqualTo("Your Vision Map code: 042917");
        assertThat(message.getText()).contains("Your code to download all your data is:", "042917", "10 minutes", "do not share this code")
                .doesNotContain("http");
    }

    @Test
    void aKhmerAccountGetsTheEmailInKhmer() {
        mailer("smtp.example.com").sendCode("me@example.com", "111222", StepUpAction.DELETE_ACCOUNT, true, 10);
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(sent.capture());
        assertThat(sent.getValue().getSubject()).isEqualTo("លេខកូដ Vision Map របស់អ្នក៖ 111222");
        assertThat(sent.getValue().getText()).contains("លុបគណនីរបស់អ្នក", "111222");
    }

    @Test
    void withoutAMailServerTheCodeIsLoggedOnlyOnALocalOrTestRun() {
        StepUpMailer local = mailer("", "local");
        assertThat(local.delivery()).isEqualTo(Delivery.LOG);
        local.sendCode("me@example.com", "123456", StepUpAction.EXPORT_DATA, false, 10);
        StepUpMailer production = mailer("", "prod");
        assertThat(production.delivery()).isEqualTo(Delivery.NONE);
        production.sendCode("me@example.com", "123456", StepUpAction.EXPORT_DATA, false, 10);
        production.sendSecurityNotice("me@example.com", false);
        verify(sender, never()).send(any(SimpleMailMessage.class));
        assertThat(mailer("smtp.example.com").delivery()).isEqualTo(Delivery.EMAIL);
    }

    @Test
    void aFailedCodeEmailIsReportedButAFailedNoticeIsNot() {
        doThrow(new MailSendException("down")).when(sender).send(any(SimpleMailMessage.class));
        StepUpMailer mailer = mailer("smtp.example.com");
        assertThatThrownBy(() -> mailer.sendCode("me@example.com", "123456", StepUpAction.EXPORT_DATA, false, 10))
                .isInstanceOf(MailSendException.class);
        mailer.sendSecurityNotice("me@example.com", false);
        mailer.sendSecurityNotice("me@example.com", true);
    }

    @Test
    void theSecurityNoticeSaysWhatHappenedInTheRightLanguage() {
        StepUpMailer mailer = mailer("smtp.example.com");
        mailer.sendSecurityNotice("me@example.com", false);
        mailer.sendSecurityNotice("me@example.com", true);
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender, org.mockito.Mockito.times(2)).send(sent.capture());
        assertThat(sent.getAllValues().get(0).getText()).contains("We signed you out everywhere else.");
        assertThat(sent.getAllValues().get(1).getSubject()).contains("ការព្រមានសុវត្ថិភាព");
    }
}
