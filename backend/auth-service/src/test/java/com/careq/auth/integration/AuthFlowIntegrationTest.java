package com.careq.auth.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Day 10 — Flow A integration test (@SpringBootTest + MockMvc + H2).
 *
 * Full HTTP round-trip through the real auth stack (SecurityConfig +
 * JwtAuthenticationFilter + JwtService + repository against H2):
 *   signup → duplicate signup rejected → login → JWT issued →
 *   the JWT actually unlocks a protected route; a missing/tampered token is rejected.
 *
 * Choice note: H2 (MySQL mode) instead of Testcontainers because CareQ's
 * JPA model is portable enough and H2 keeps the suite runnable on any
 * machine with zero Docker dependency.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    private static final String EMAIL = "flow." + UUID.randomUUID().toString().substring(0, 8) + "@careq.com";
    private static final String SIGNUP_BODY =
            "{\"fullName\":\"Flow Patient\",\"email\":\"" + EMAIL + "\",\"password\":\"password123\",\"role\":\"PATIENT\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void flowA_signupLoginAndJwtGrantsAccessToProtectedRoute() throws Exception {
        // 1. Signup returns 201 with a real JWT.
        MvcResult signup = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIGNUP_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("PATIENT"))
                .andReturn();

        JsonNode signupBody = objectMapper.readTree(signup.getResponse().getContentAsString());
        String signupToken = signupBody.get("token").asText();
        assertThat(signupToken).isNotBlank();

        // 2. Duplicate email signup → 409 (shared error shape with path).
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIGNUP_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.path").value("/api/auth/signup"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already")));

        // 3. Login with the same credentials → 200 + a usable JWT.
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        String loginToken = objectMapper.readTree(login.getResponse().getContentAsString())
                .get("token").asText();
        assertThat(loginToken).isNotBlank();

        // 4. Wrong password → 401.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());

        // 5. The JWT from login is accepted by the security filter chain: the
        //    request passes the `authenticated()` rule on /api/auth/** (it is
        //    NOT rejected with 403 the way unauthenticated requests are).
        //    auth-service has no handler for GET /api/auth/me, so the status
        //    after passing security is a routing/advice outcome, not 403.
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + loginToken))
                .andExpect(status().is(org.hamcrest.Matchers.not(403)));

        // 6. Without a token the same protected route is rejected by Spring Security.
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isForbidden());

        // 7. A tampered token is rejected too.
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + loginToken + "tampered"))
                .andExpect(status().isForbidden());
    }

    @Test
    void signupValidation_BlankFields_Returns400WithSharedErrorShape() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"\",\"email\":\"not-an-email\",\"password\":\"\",\"role\":\"PATIENT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.path").value("/api/auth/signup"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors").isArray())
                .andExpect(jsonPath("$.validationErrors[0].field").isNotEmpty());
    }

    @Test
    void login_UnknownEmail_Returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@careq.com\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized());
    }
}
