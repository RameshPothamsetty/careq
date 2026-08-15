package com.careq.auth.entity;

/**
 * What a row in {@code auth_tokens} is for (email verification +
 * password reset). Each purpose has its own expiry policy:
 * <ul>
 *   <li>{@link #VERIFY_EMAIL} — 24h, issued at signup, consumed by the
 *       verify link in the email.</li>
 *   <li>{@link #RESET_PASSWORD} — 30 min, issued by forgot-password,
 *       consumed by the reset link.</li>
 * </ul>
 */
public enum AuthTokenPurpose {
    VERIFY_EMAIL,
    RESET_PASSWORD
}
