package com.careq.auth.integration;

import com.careq.auth.entity.AuthTokenPurpose;
import com.careq.auth.service.AuthTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * full email-verification + password-reset HTTP round trip with
 * verification ENFORCED (the test profile default has it off, so this class
 * flips it on):
 *
 *   signup → no JWT (verificationRequired) → login blocked (403
 *   EMAIL_NOT_VERIFIED) → token consumed → login works → forgot-password →
 *   reset-password → login with the new password.
 *
 * The raw token normally travels by email; here it's issued directly through
 * the real AuthTokenService so the HTTP endpoints can be exercised.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.auth.email-verification-enabled=true")
class AuthVerificationFlowIntegrationTest {

    private static final String PASSWORD = "password123";
    private static final String EMAIL = "verify." + UUID.randomUUID().toString().substring(0, 8) + "@careq.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthTokenService authTokenService;

    @Test
    void fullVerificationAndResetJourney() throws Exception {
        // 1. Signup → 201, NO token, verificationRequired=true.
        MvcResult signup = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Verify Patient\",\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\",\"role\":\"PATIENT\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.verificationRequired").value(true))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Verification email sent")))
                .andReturn();

        String userId = objectMapper.readTree(signup.getResponse().getContentAsString()).get("userId").asText();

        // 2. Login before verification → 403 with code EMAIL_NOT_VERIFIED.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        // 3. Resend verification while pending → a fresh link is "sent".
        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("on its way")));

        // 4. Consume a verification token (issued directly — the email link
        //    normally carries it) → 200, account verified.
        String rawVerifyToken = authTokenService.issue(userId, AuthTokenPurpose.VERIFY_EMAIL, 1440);
        mockMvc.perform(get("/api/auth/verify").param("token", rawVerifyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("verified")));

        // 5. Reusing the same token → 400 INVALID_TOKEN (one-time).
        mockMvc.perform(get("/api/auth/verify").param("token", rawVerifyToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        // 6. Login now works.
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        assertThat(objectMapper.readTree(login.getResponse().getContentAsString()).get("token").asText()).isNotBlank();

        // 7. Forgot password → generic message (no account enumeration).
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("reset link is on its way")));

        // 8. Reset with a valid token → 200; login with the NEW password.
        String rawResetToken = authTokenService.issue(userId, AuthTokenPurpose.RESET_PASSWORD, 30);
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + rawResetToken + "\",\"newPassword\":\"brandNewPass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("has been reset")));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"brandNewPass123\"}"))
                .andExpect(status().isOk());

        // 9. Reset with a garbage token → 400 INVALID_TOKEN.
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"garbage\",\"newPassword\":\"whatever123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }
}
