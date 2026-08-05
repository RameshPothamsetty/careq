package com.careq.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "Profile update payload. Partial update — only non-null fields are changed.")
public class UpdateUserProfileRequestDto {

    @Schema(description = "Phone number (international format)", example = "+919876543210")
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Phone must be a valid international phone number")
    @Size(max = 20, message = "Phone must not exceed 20 characters")
    private String phone;

    @Schema(description = "Street address", example = "123 Main Street, Bengaluru")
    @Size(max = 500, message = "Address must not exceed 500 characters")
    private String address;

    @Schema(description = "Date of birth (yyyy-MM-dd)", example = "1990-05-15")
    private LocalDate dateOfBirth;

    @Schema(description = "Gender", example = "MALE", allowableValues = {"MALE", "FEMALE", "OTHER"})
    @Pattern(regexp = "^(?i)(MALE|FEMALE|OTHER)$", message = "Gender must be one of: MALE, FEMALE, OTHER")
    private String gender;

    @Schema(description = "Profile picture URL (normally set via the picture upload endpoint)", example = "/api/users/profile-pictures/abc123.jpg")
    @Size(max = 500, message = "Profile picture URL must not exceed 500 characters")
    private String profilePictureUrl;

    public UpdateUserProfileRequestDto() {
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getProfilePictureUrl() {
        return profilePictureUrl;
    }

    public void setProfilePictureUrl(String profilePictureUrl) {
        this.profilePictureUrl = profilePictureUrl;
    }
}
