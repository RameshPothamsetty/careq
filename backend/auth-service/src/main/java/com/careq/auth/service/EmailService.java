package com.careq.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Transactional email via the SendGrid v3 REST API . Used for the
 * verification email and password-reset links.
 *
 * <p>FAIL-OPEN BY DESIGN (same contract as the web-push delivery):
 * when {@code app.mail.sendgrid-api-key} is not configured the service logs
 * a warning and skips sending — the auth flow still works, it just skips
 * the email step (accounts stay unverified, resend is available). A failed
 * SendGrid call is logged and swallowed, never thrown: verification and
 * reset are resumable flows, so a transient email failure must not surface
 * as a 500 to the user.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final RestClient restClient;
    private final String apiKey;
    private final String from;
    private final String frontendBaseUrl;

    public EmailService(RestClient.Builder restClientBuilder,
                        @Value("${app.mail.sendgrid-api-key:}") String apiKey,
                        @Value("${app.mail.from:careq@example.com}") String from,
                        @Value("${app.frontend.base-url:http://localhost:3030}") String frontendBaseUrl) {
        this.apiKey = apiKey;
        this.from = from;
        this.frontendBaseUrl = frontendBaseUrl;
        this.restClient = restClientBuilder.build();
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
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("app.mail.sendgrid-api-key not configured — skipping email to {} (subject: {})", to, subject);
            return;
        }
        try {
            Map<String, Object> payload = Map.of(
                    "personalizations", List.of(Map.of("to", List.of(Map.of("email", to)))),
                    "from", Map.of("email", from, "name", "CareQ"),
                    "subject", subject,
                    "content", List.of(Map.of("type", "text/plain", "value", text))
            );
            restClient.post()
                    .uri("https://api.sendgrid.com/v3/mail/send")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Sent transactional email to {} (subject: {})", to, subject);
        } catch (Exception e) {
            // Fail-open: log loudly and move on — verification/reset are resumable.
            log.error("SendGrid email to {} failed (subject: {}): {}", to, subject, e.getMessage());
        }
    }
}
