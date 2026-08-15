package com.careq.auth.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Central error handling (every response uses the shared
 * ErrorResponseDto shape with {@code timestamp / status / error / message /
 * path} and a {@code validationErrors} list on 400 validation failures).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ErrorResponseDto> handleDuplicateEmail(DuplicateEmailException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponseDto> handleInvalidCredentials(InvalidCredentialsException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage(), request);
    }

    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<ErrorResponseDto> handleEmailNotVerified(EmailNotVerifiedException ex, HttpServletRequest request) {
        return buildWithCode(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), "EMAIL_NOT_VERIFIED", request);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponseDto> handleRateLimit(RateLimitExceededException ex, HttpServletRequest request) {
        return buildWithCode(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", ex.getMessage(), "RATE_LIMITED", request);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponseDto> handleInvalidToken(InvalidTokenException ex, HttpServletRequest request) {
        return buildWithCode(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), "INVALID_TOKEN", request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponseDto> handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Missing Header",
                "Required request header '" + ex.getHeaderName() + "' is missing", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidationErrors(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ValidationError> validationErrors = ex.getBindingResult().getAllErrors().stream()
                .map(error -> {
                    if (error instanceof FieldError fieldError) {
                        return new ValidationError(fieldError.getField(), fieldError.getDefaultMessage());
                    }
                    return new ValidationError(null, error.getDefaultMessage());
                })
                .collect(Collectors.toList());

        ErrorResponseDto error = new ErrorResponseDto(
                HttpStatus.BAD_REQUEST.value(),
                "Validation Failed",
                "Request validation failed"
        );
        error.setPath(request.getRequestURI());
        error.setValidationErrors(validationErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDto> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request);
    }

    /**
     * BUG-1 fix: an unmapped route used to be swallowed by the catch-all below
     * and returned 500. Spring throws NoResourceFoundException when no handler
     * matches; it now returns a proper 404 with the shared error shape.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "Not Found",
                "No endpoint found for " + request.getMethod() + " " + request.getRequestURI(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleGeneralException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        ErrorResponseDto error = new ErrorResponseDto(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "An unexpected error occurred"
        );
        error.setPath(request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    private ResponseEntity<ErrorResponseDto> build(HttpStatus status, String error, String message, HttpServletRequest request) {
        return buildWithCode(status, error, message, null, request);
    }

    private ResponseEntity<ErrorResponseDto> buildWithCode(HttpStatus status, String error, String message, String code, HttpServletRequest request) {
        ErrorResponseDto dto = new ErrorResponseDto(status.value(), error, message);
        dto.setCode(code);
        dto.setPath(request.getRequestURI());
        return ResponseEntity.status(status).body(dto);
    }
}
