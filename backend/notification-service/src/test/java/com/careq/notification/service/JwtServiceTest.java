package com.careq.notification.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JWT validation for the real-time socket's STOMP CONNECT frame.
 */
class JwtServiceTest {

    /** Must match the app default in application.yml (>= 32 bytes for HS256). */
    private static final String SECRET = "CareQTempSecretKeyForDevelopmentOnlyChangeInProduction2024";

    private final JwtService jwtService = new JwtService(SECRET);

    private SecretKey key() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    private String token(String subject, String role, Date expiry) {
        return Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .expiration(expiry)
                .issuedAt(new Date())
                .signWith(key())
                .compact();
    }

    @Test
    void parse_ValidToken_ReturnsUserIdAndRole() {
        String token = token("user-123", "PATIENT", Date.from(Instant.now().plusSeconds(3600)));

        Optional<JwtService.WsPrincipal> principal = jwtService.parse(token);

        assertThat(principal).isPresent();
        assertThat(principal.get().userId()).isEqualTo("user-123");
        assertThat(principal.get().role()).isEqualTo("PATIENT");
    }

    @Test
    void parse_ExpiredToken_ReturnsEmpty() {
        String token = token("user-123", "PATIENT", Date.from(Instant.now().minusSeconds(10)));

        assertThat(jwtService.parse(token)).isEmpty();
    }

    @Test
    void parse_WrongSecret_ReturnsEmpty() {
        String token = Jwts.builder()
                .subject("user-123")
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor("AnotherVeryLongSecretKeyForTestingOnly123456789".getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(jwtService.parse(token)).isEmpty();
    }

    @Test
    void parse_Garbage_ReturnsEmpty() {
        assertThat(jwtService.parse("not-a-jwt")).isEmpty();
        assertThat(jwtService.parse("")).isEmpty();
        assertThat(jwtService.parse(null)).isEmpty();
    }
}
