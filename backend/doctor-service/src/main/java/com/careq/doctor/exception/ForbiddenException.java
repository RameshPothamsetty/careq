package com.careq.doctor.exception;

/**
 * Thrown when an authenticated caller lacks the role required by an endpoint.
 * Mapped to 403 by {@link GlobalExceptionHandler}.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
