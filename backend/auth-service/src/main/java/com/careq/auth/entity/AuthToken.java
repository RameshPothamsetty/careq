package com.careq.auth.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One-time security token for email verification and password reset
 * . Only the SHA-256 hash of the raw token is stored — the raw
 * value travels in the email link and is never persisted, so a database
 * leak cannot be replayed as tokens.
 *
 * <p>The row doubles as the account's verification state: an account is
 * <em>pending verification</em> iff it has a VERIFY_EMAIL row with
 * {@code usedAt == null}. Legacy accounts created before verification was enabled have no
 * rows at all and are therefore treated as already verified — no migration
 * of existing users is needed.
 */
@Entity
@Table(name = "auth_tokens")
public class AuthToken {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 20)
    private AuthTokenPurpose purpose;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public AuthToken() {
    }

    public AuthToken(String userId, AuthTokenPurpose purpose, String tokenHash, LocalDateTime expiresAt) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.purpose = purpose;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public AuthTokenPurpose getPurpose() {
        return purpose;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(LocalDateTime usedAt) {
        this.usedAt = usedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
