package com.careq.auth.exception;

/**
 * Too many attempts against a public auth endpoint (login brute-force,
 * signup spam, resend/forgot abuse) — Day 17. Maps to 429 + code
 * RATE_LIMITED.
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
