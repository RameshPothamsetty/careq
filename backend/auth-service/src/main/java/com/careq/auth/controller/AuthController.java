package com.careq.auth.controller;

import com.careq.auth.dto.AuthResponseDto;
import com.careq.auth.dto.LoginRequestDto;
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
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication endpoints (Day 9: fully documented with OpenAPI).
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
            description = "Creates a user with the given role (PATIENT, DOCTOR or ADMIN) and returns a JWT in the response. " +
                    "Signing up with an email that is already registered returns 409 Conflict.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User created; JWT returned",
                    content = @Content(schema = @Schema(implementation = AuthResponseDto.class),
                            examples = @ExampleObject(value = "{\"token\":\"eyJhbGciOiJIUzI1NiJ9...\",\"tokenType\":\"Bearer\",\"userId\":\"550e8400-e29b-41d4-a716-446655440000\",\"email\":\"john@careq.com\",\"fullName\":\"John Patient\",\"role\":\"PATIENT\"}"))),
            @ApiResponse(responseCode = "400", description = "Validation failed (blank fields, invalid email, invalid role)",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "409", description = "Email already registered",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PostMapping("/signup")
    public ResponseEntity<AuthResponseDto> signup(@Valid @RequestBody SignupRequestDto request) {
        AuthResponseDto response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Log in",
            description = "Authenticates with email + password and returns a JWT. The token must be sent as " +
                    "\"Authorization: Bearer <token>\" on every other CareQ API call.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Authenticated; JWT returned",
                    content = @Content(schema = @Schema(implementation = AuthResponseDto.class),
                            examples = @ExampleObject(value = "{\"token\":\"eyJhbGciOiJIUzI1NiJ9...\",\"tokenType\":\"Bearer\",\"userId\":\"550e8400-e29b-41d4-a716-446655440000\",\"email\":\"john@careq.com\",\"fullName\":\"John Patient\",\"role\":\"PATIENT\"}"))),
            @ApiResponse(responseCode = "400", description = "Validation failed (blank email/password)",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials or deactivated account",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody LoginRequestDto request) {
        AuthResponseDto response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}
