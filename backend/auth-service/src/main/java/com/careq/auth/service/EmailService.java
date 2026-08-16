package com.careq.auth.service;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Transactional email via Gmail SMTP (JavaMailSender). Used for the
 * verification email and password-reset links. Emails arrive from the
 * configured {@code app.mail.from} address with the display name "CareQ".
 *
 * <p>FAIL-OPEN BY DESIGN (same contract as the web-push delivery):
 * when {@code app.mail.username} / {@code app.mail.password} are not
 * configured the service logs a warning and skips sending — the auth flow
 * still works, it just skips the email step (accounts stay unverified,
 * resend is available). A failed send is logged and swallowed, never thrown:
 * verification and reset are resumable flows, so a transient email failure
 * must not surface as a 500 to the user.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String username;
    private final String password;
    private final String from;
    private final String frontendBaseUrl;

    public EmailService(JavaMailSender mailSender,
                        @Value("${app.mail.username:}") String username,
                        @Value("${app.mail.password:}") String password,
                        @Value("${app.mail.from:careq@example.com}") String from,
                        @Value("${app.frontend.base-url:http://localhost:3030}") String frontendBaseUrl) {
        this.mailSender = mailSender;
        this.username = username;
        this.password = password;
        this.from = from;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /** Sends the "verify your email" email with a one-time link. */
    public void sendVerificationEmail(String to, String fullName, String token) {
        String link = frontendBaseUrl + "/verify-email?token=" + token;
        String subject = "Verify your CareQ account";
        String body = "Hi " + fullName + ",\n\n"
                + "Welcome to CareQ! Please verify your email address to activate your account:\n\n"
                + link + "\n\n"
                + "This link expires in 24 hours. If you didn't create a CareQ account, you can ignore this email.\n\n"
                + "— CareQ";
        send(to, subject, body);
    }

    /** Sends the password-reset email with a one-time link. */
    public void sendPasswordResetEmail(String to, String fullName, String token) {
        String link = frontendBaseUrl + "/reset-password?token=" + token;
        String subject = "Reset your CareQ password";
        String body = "Hi " + fullName + ",\n\n"
                + "We received a request to reset your CareQ password. Use the link below to choose a new one:\n\n"
                + link + "\n\n"
                + "This link expires in 30 minutes. If you didn't request this, you can safely ignore this email.\n\n"
                + "— CareQ";
        send(to, subject, body);
    }

    private void send(String to, String subject, String text) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            log.warn("app.mail.username/password not configured — skipping email to {} (subject: {})", to, subject);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            message.setFrom(new InternetAddress(from, "CareQ"));
            message.setRecipients(MimeMessage.RecipientType.TO, to);
            message.setSubject(subject, "UTF-8");
            message.setText(text, "UTF-8");
            mailSender.send(message);
            log.info("Sent transactional email to {} (subject: {})", to, subject);
        } catch (Exception e) {
            // Fail-open: log loudly and move on — verification/reset are resumable.
            log.error("Email to {} failed (subject: {}): {}", to, subject, e.getMessage());
        }
    }
}
