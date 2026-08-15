package com.careq.auth.service;

import com.careq.auth.entity.AuthToken;
import com.careq.auth.entity.AuthTokenPurpose;
import com.careq.auth.repository.AuthTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Issues and consumes one-time email tokens (Day 17).
 *
 * <p>The raw token (32 random bytes, Base64URL) is only ever returned to
 * the caller for inclusion in the email link; the database stores its
 * SHA-256 hex digest. Lookups happen by digest, and a consumed or expired
 * token can never be replayed.
 */
@Service
public class AuthTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int RAW_TOKEN_BYTES = 32;

    private final AuthTokenRepository authTokenRepository;

    public AuthTokenService(AuthTokenRepository authTokenRepository) {
        this.authTokenRepository = authTokenRepository;
    }

    /**
     * Issues a fresh one-time token and persists only its hash. Any previous
     * token of the same purpose for this user is invalidated (resend /
     * repeated forgot-password rotates the link).
     *
     * @return the raw token to embed in the email link (never persisted)
     */
    @Transactional
    public String issue(String userId, AuthTokenPurpose purpose, int ttlMinutes) {
        String raw = generateRaw();
        AuthToken token = new AuthToken(
                userId,
                purpose,
                sha256(raw),
                LocalDateTime.now().plus(ttlMinutes, ChronoUnit.MINUTES)
        );
        authTokenRepository.deleteByUserIdAndPurpose(userId, purpose);
        authTokenRepository.save(token);
        return raw;
    }

    /**
     * Consumes a raw token for the given purpose. Returns the owning user id
     * when the token exists, is unused and unexpired; the row is marked used
     * (one-time). Empty result otherwise — never an exception.
     */
    @Transactional
    public Optional<String> consume(String raw, AuthTokenPurpose purpose) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        Optional<AuthToken> found = authTokenRepository
                .findByTokenHashAndPurpose(sha256(raw), purpose);
        if (found.isEmpty() || found.get().getUsedAt() != null
                || found.get().getExpiresAt().isBefore(LocalDateTime.now())) {
            return Optional.empty();
        }
        AuthToken token = found.get();
        token.setUsedAt(LocalDateTime.now());
        authTokenRepository.save(token);
        return Optional.of(token.getUserId());
    }

    /** True iff the user has a pending (unused, unexpired) VERIFY_EMAIL token. */
    @Transactional(readOnly = true)
    public boolean hasPendingVerification(String userId) {
        return authTokenRepository.existsByUserIdAndPurposeAndUsedAtIsNull(userId, AuthTokenPurpose.VERIFY_EMAIL);
    }

    String generateRaw() {
        byte[] bytes = new byte[RAW_TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
