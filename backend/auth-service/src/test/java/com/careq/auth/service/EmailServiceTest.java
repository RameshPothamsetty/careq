package com.careq.auth.service;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Gmail SMTP emails: correct subject / from (display name "CareQ") /
 * recipient and verify/reset links; and fail-open when the SMTP
 * credentials are absent.
 */
class EmailServiceTest {

    private JavaMailSender mailSender() {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));
        return sender;
    }

    private EmailService service(JavaMailSender sender) {
        return new EmailService(sender, "rap53748@gmail.com", "app-password",
                "rap53748@gmail.com", "https://careq.example.com");
    }

    @Test
    void sendVerificationEmail_BuildsMailMessage() throws Exception {
        JavaMailSender sender = mailSender();
        EmailService service = service(sender);

        assertThatCode(() -> service.sendVerificationEmail("john@careq.com", "John Patient", "abc123"))
                .doesNotThrowAnyException();

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        MimeMessage msg = captor.getValue();
        assertThat(msg.getSubject()).isEqualTo("Verify your CareQ account");
        assertThat(msg.getFrom()[0].toString()).contains("CareQ").contains("rap53748@gmail.com");
        assertThat(msg.getRecipients(Message.RecipientType.TO)[0].toString()).contains("john@careq.com");
        assertThat(msg.getContent().toString())
                .contains("https://careq.example.com/verify-email?token=abc123");
    }

    @Test
    void sendPasswordResetEmail_ContainsResetLink() throws Exception {
        JavaMailSender sender = mailSender();
        EmailService service = service(sender);

        assertThatCode(() -> service.sendPasswordResetEmail("john@careq.com", "John Patient", "reset-1"))
                .doesNotThrowAnyException();

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        MimeMessage msg = captor.getValue();
        assertThat(msg.getSubject()).isEqualTo("Reset your CareQ password");
        assertThat(msg.getContent().toString())
                .contains("https://careq.example.com/reset-password?token=reset-1");
    }

    @Test
    void missingCredentials_DoesNotSendMail() {
        JavaMailSender sender = mailSender();
        EmailService service = new EmailService(sender, "", "", "careq@example.com", "https://careq.example.com");

        assertThatCode(() -> service.sendVerificationEmail("john@careq.com", "John Patient", "abc123"))
                .doesNotThrowAnyException();
        verify(sender, never()).send(any(MimeMessage.class));
    }
}
