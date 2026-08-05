package com.careq.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Registration request payload.")
public class SignupRequestDto {

    @Schema(description = "User's full display name", example = "John Patient", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Full name is required")
    @Size(max = 255, message = "Full name must not exceed 255 characters")
    private String fullName;

    @Schema(description = "Login email (must be unique across the platform)", example = "john@careq.com", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    @Schema(description = "Password (min 6 characters)", example = "password123", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
    private String password;

    @Schema(description = "Account role", example = "PATIENT", allowableValues = {"PATIENT", "DOCTOR", "ADMIN"}, requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Role is required")
    @Pattern(regexp = "PATIENT|DOCTOR|ADMIN", message = "Role must be one of: PATIENT, DOCTOR, ADMIN")
    private String role;

    public SignupRequestDto() {
    }

    public SignupRequestDto(String fullName, String email, String password, String role) {
        this.fullName = fullName;
        this.email = email;
        this.password = password;
        this.role = role;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
