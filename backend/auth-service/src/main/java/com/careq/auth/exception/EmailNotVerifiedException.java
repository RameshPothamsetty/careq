package com.careq.auth.exception;

/**
 * Login attempted while the account still has a pending (unconsumed)
 * verification email . Maps to 403 + code EMAIL_NOT_VERIFIED so
 * the frontend can show the "check your inbox / resend" screen instead of
 * a generic error.
 */
public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException(String message) {
        super(message);
    }
}
