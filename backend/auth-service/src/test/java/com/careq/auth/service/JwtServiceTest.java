package com.careq.auth.service;

import com.careq.auth.entity.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JwtService} (Day 2 plan, built Day 10).
 * Uses a real secret key — no mocking, so the token round-trip is genuinely verified.
 */
class JwtServiceTest {

    /** Must be at least 32 bytes for HS256 (jjwt requirement). */
    private static final String SECRET = "careq-test-secret-key-with-at-least-32-chars!!";
    private static final long EXPIRATION_MS = 3_600_000L;

    private final JwtService jwtService = new JwtService(SECRET, EXPIRATION_MS);

    @Test
    void generateToken_ShouldProduceValidToken() {
        String token = jwtService.generateToken("user-1", "john@careq.com", "John Patient", Role.DOCTOR);

        assertThat(token).isNotBlank();
        assertThat(jwtService.isTokenValid(token)).isTrue();
        assertThat(jwtService.extractUserId(token)).isEqualTo("user-1");
        assertThat(jwtService.extractEmail(token)).isEqualTo("john@careq.com");
        assertThat(jwtService.extractRole(token)).isEqualTo("DOCTOR");
    }

    @Test
    void isTokenValid_ExpiredToken_ShouldReturnFalse() {
        // A negative expiration makes the issued token already expired.
        JwtService expiredService = new JwtService(SECRET, -1_000L);
        String token = expiredService.generateToken("user-1", "john@careq.com", "John Patient", Role.PATIENT);

        assertThat(expiredService.isTokenValid(token)).isFalse();
    }

    @Test
    void isTokenValid_TamperedToken_ShouldReturnFalse() {
        String token = jwtService.generateToken("user-1", "john@careq.com", "John Patient", Role.PATIENT);
        String[] parts = token.split("\\.");

        String tampered = parts[0] + "." + parts[1] + "." + flipFirstChar(parts[2]);

        assertThat(tampered).isNotEqualTo(token);
        assertThat(jwtService.isTokenValid(tampered)).isFalse();
    }

    @Test
    void isTokenValid_GarbageToken_ShouldReturnFalse() {
        assertThat(jwtService.isTokenValid("not-a-real-jwt")).isFalse();
        assertThat(jwtService.isTokenValid("")).isFalse();
        assertThat(jwtService.isTokenValid(null)).isFalse();
    }

    private String flipFirstChar(String segment) {
        char original = segment.charAt(0);
        char replacement = original == 'A' ? 'B' : 'A';
        return replacement + segment.substring(1);
    }
}
