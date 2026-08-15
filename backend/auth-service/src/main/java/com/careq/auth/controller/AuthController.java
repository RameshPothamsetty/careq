package com.careq.auth.controller;

import com.careq.auth.dto.AuthResponseDto;
import com.careq.auth.dto.ForgotPasswordRequestDto;
import com.careq.auth.dto.LoginRequestDto;
import com.careq.auth.dto.ResendVerificationRequestDto;
import com.careq.auth.dto.ResetPasswordRequestDto;
import com.careq.auth.dto.SignupRequestDto;
import com.careq.auth.exception.ErrorResponseDto;
import com.careq.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication endpoints (fully documented with OpenAPI;
 * email verification + password reset + rate limiting).
 */
@Tag(name = "Authentication", description = "Public registration and login endpoints — no Bearer token required.")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Register a new user",
            description = "Creates a user with the given role (PATIENT, DOCTOR or ADMIN). With email verification " +
                    "enabled (production), the response contains NO token and the account stays locked until the " +
                    "verification link in the email is clicked (verificationRequired=true). Without it (local dev), " +
                    "a JWT is returned immediately as before. Signing up with an already-registered email returns 409.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User created; JWT or verification-required response",
                    content = @Content(schema = @Schema(implementation = AuthResponseDto.class),
                            examples = @ExampleObject(value = "{\"token\":null,\"tokenType\":\"Bearer\",\"userId\":\"550e8400-e29b-41d4-a716-446655440000\",\"email\":\"john@careq.com\",\"fullName\":\"John Patient\",\"role\":\"PATIENT\",\"message\":\"Verification email sent to john@careq.com — click the link in it to activate your account.\",\"verificationRequired\":true}"))),
            @ApiResponse(responseCode = "400", description = "Validation failed (blank fields, invalid email, invalid role)",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "409", description = "Email already registered",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "429", description = "Too many signups from this IP",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PostMapping("/signup")
    public ResponseEntity<AuthResponseDto> signup(@Valid @RequestBody SignupRequestDto request, HttpServletRequest http) {
        AuthResponseDto response = authService.signup(request, clientIp(http));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Log in",
            description = "Authenticates with email + password and returns a JWT. Returns 403 with code " +
                    "EMAIL_NOT_VERIFIED when the account hasn't clicked its verification link yet, and 429 with " +
                    "code RATE_LIMITED after repeated failed attempts (brute-force guard).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Authenticated; JWT returned",
                    content = @Content(schema = @Schema(implementation = AuthResponseDto.class),
                            examples = @ExampleObject(value = "{\"token\":\"eyJhbGciOiJIUzI1NiJ9...\",\"tokenType\":\"Bearer\",\"userId\":\"550e8400-e29b-41d4-a716-446655440000\",\"email\":\"john@careq.com\",\"fullName\":\"John Patient\",\"role\":\"PATIENT\"}"))),
            @ApiResponse(responseCode = "400", description = "Validation failed (blank email/password)",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials or deactivated account",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Email not verified yet (code EMAIL_NOT_VERIFIED)",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "429", description = "Too many failed attempts (code RATE_LIMITED)",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody LoginRequestDto request, HttpServletRequest http) {
        AuthResponseDto response = authService.login(request, clientIp(http));
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Verify email with the one-time link",
            description = "Consumed by the link in the verification email (?token=...). Marks the account verified " +
                    "and lets it sign in. Invalid/expired/used tokens return 400 with code INVALID_TOKEN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Email verified",
                    content = @Content(schema = @Schema(implementation = AuthResponseDto.class),
                            examples = @ExampleObject(value = "{\"message\":\"Your email has been verified — you can now sign in.\"}"))),
            @ApiResponse(responseCode = "400", description = "Invalid, expired or already-used token (code INVALID_TOKEN)",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @GetMapping("/verify")
    public ResponseEntity<AuthResponseDto> verify(@RequestParam("token") String token) {
        return ResponseEntity.ok(authService.verifyEmail(token));
    }

    @Operation(summary = "Resend the verification email",
            description = "Sends a fresh verification link (rotating the previous one). Returns 200 with a message; " +
                    "never distinguishes between unknown and verified emails beyond a helpful message.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Outcome message",
                    content = @Content(schema = @Schema(implementation = AuthResponseDto.class),
                            examples = @ExampleObject(value = "{\"message\":\"A new verification email is on its way to john@careq.com.\"}"))),
            @ApiResponse(responseCode = "429", description = "Too many resend requests (code RATE_LIMITED)",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PostMapping("/resend-verification")
    public ResponseEntity<AuthResponseDto> resendVerification(@Valid @RequestBody ResendVerificationRequestDto request, HttpServletRequest http) {
        return ResponseEntity.ok(authService.resendVerification(request.getEmail(), clientIp(http)));
    }

    @Operation(summary = "Request a password-reset link",
            description = "Emails a one-time reset link (?token=...) that expires in 30 minutes. Always returns 200 " +
                    "with a generic message so the endpoint can't be used to enumerate accounts.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Outcome message",
                    content = @Content(schema = @Schema(implementation = AuthResponseDto.class),
                            examples = @ExampleObject(value = "{\"message\":\"If an account exists for john@careq.com, a reset link is on its way.\"}"))),
            @ApiResponse(responseCode = "429", description = "Too many reset requests (code RATE_LIMITED)",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PostMapping("/forgot-password")
    public ResponseEntity<AuthResponseDto> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDto request, HttpServletRequest http) {
        return ResponseEntity.ok(authService.forgotPassword(request.getEmail(), clientIp(http)));
    }

    @Operation(summary = "Complete a password reset",
            description = "Exchanges the one-time token from the reset email for a new password. Invalid/expired " +
                    "tokens return 400 with code INVALID_TOKEN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password reset",
                    content = @Content(schema = @Schema(implementation = AuthResponseDto.class),
                            examples = @ExampleObject(value = "{\"message\":\"Your password has been reset — you can now sign in.\"}"))),
            @ApiResponse(responseCode = "400", description = "Invalid, expired or already-used token (code INVALID_TOKEN)",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PostMapping("/reset-password")
    public ResponseEntity<AuthResponseDto> resetPassword(@Valid @RequestBody ResetPasswordRequestDto request) {
        return ResponseEntity.ok(authService.resetPassword(request.getToken(), request.getNewPassword()));
    }

    /**
     * Real client IP: the gateway proxies every request, so the socket peer
     * is the gateway — the browser's IP arrives in the X-Forwarded-For
     * header (Spring Cloud Gateway appends it). Fall back to remoteAddr for
     * direct (non-gateway) calls in local dev.
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
