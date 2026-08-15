package com.careq.auth.exception;

/**
 * A verification/reset token is missing, unknown, already used, or expired
 * (Day 17). Maps to 400 + code INVALID_TOKEN. The message deliberately
 * covers all four cases so attackers can't distinguish valid-but-used
 * tokens from garbage.
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }
}
