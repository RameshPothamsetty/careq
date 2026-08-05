package com.careq.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Authentication response containing the JWT and basic identity.")
public class AuthResponseDto {

    @Schema(description = "JWT access token. Send as \"Authorization: Bearer <token>\" on all other calls.", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String token;

    @Schema(description = "Token type (always Bearer)", example = "Bearer")
    private String tokenType;

    @Schema(description = "User UUID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String userId;

    @Schema(description = "User email", example = "john@careq.com")
    private String email;

    @Schema(description = "User's full display name", example = "John Patient")
    private String fullName;

    @Schema(description = "Account role", example = "PATIENT", allowableValues = {"PATIENT", "DOCTOR", "ADMIN"})
    private String role;

    public AuthResponseDto() {
    }

    public AuthResponseDto(String token, String userId, String email, String fullName, String role) {
        this.token = token;
        this.tokenType = "Bearer";
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
