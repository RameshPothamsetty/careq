package com.careq.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * SendGrid email payloads: correct endpoint, bearer auth, subject
 * and recipient; and fail-open when the API key is absent.
 */
class EmailServiceTest {

    @Test
    void sendVerificationEmail_BuildsSendGridPayload() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        EmailService service = new EmailService(builder, "sg-key", "careq@careq.com", "https://careq.example.com");

        server.expect(requestTo("https://api.sendgrid.com/v3/mail/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sg-key"))
                .andExpect(jsonPath("$.personalizations[0].to[0].email").value("john@careq.com"))
                .andExpect(jsonPath("$.from.email").value("careq@careq.com"))
                .andExpect(jsonPath("$.subject").value("Verify your CareQ account"))
                .andExpect(jsonPath("$.content[0].value").value(
                        org.hamcrest.Matchers.containsString("https://careq.example.com/verify-email?token=abc123")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatCode(() -> service.sendVerificationEmail("john@careq.com", "John Patient", "abc123"))
                .doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void sendPasswordResetEmail_ContainsResetLink() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        EmailService service = new EmailService(builder, "sg-key", "careq@careq.com", "https://careq.example.com");

        server.expect(requestTo("https://api.sendgrid.com/v3/mail/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.subject").value("Reset your CareQ password"))
                .andExpect(jsonPath("$.content[0].value").value(
                        org.hamcrest.Matchers.containsString("https://careq.example.com/reset-password?token=reset-1")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatCode(() -> service.sendPasswordResetEmail("john@careq.com", "John Patient", "reset-1"))
                .doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void missingApiKey_DoesNotCallSendGrid() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        EmailService service = new EmailService(builder, "", "careq@careq.com", "https://careq.example.com");

        assertThatCode(() -> service.sendVerificationEmail("john@careq.com", "John Patient", "abc123"))
                .doesNotThrowAnyException();
        server.verify(); // no expectations registered → any request would fail the verify
    }
}
