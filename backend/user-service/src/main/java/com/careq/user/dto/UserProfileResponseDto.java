package com.careq.user.dto;

import com.careq.user.entity.UserProfile;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "A user's profile (shared shape for all roles).")
public class UserProfileResponseDto {

    @Schema(description = "Internal profile row id", example = "1")
    private Long id;

    @Schema(description = "User UUID (matches the JWT subject)", example = "550e8400-e29b-41d4-a716-446655440000")
    private String userId;

    @Schema(description = "Display name", example = "John Patient")
    private String fullName;

    @Schema(description = "Email address", example = "john@careq.com")
    private String email;

    @Schema(description = "Phone number", example = "+919876543210")
    private String phone;

    @Schema(description = "Street address", example = "123 Main Street, Bengaluru")
    private String address;

    @Schema(description = "Date of birth (yyyy-MM-dd)", example = "1990-05-15")
    private LocalDate dateOfBirth;

    @Schema(description = "Gender", example = "MALE", allowableValues = {"MALE", "FEMALE", "OTHER"})
    private String gender;

    @Schema(description = "Public URL of the stored profile picture, if any", example = "/api/users/profile-pictures/abc123.jpg")
    private String profilePictureUrl;

    @Schema(description = "Account role", example = "PATIENT", allowableValues = {"PATIENT", "DOCTOR", "ADMIN"})
    private String role;

    public UserProfileResponseDto() {
    }

    public static UserProfileResponseDto fromEntity(UserProfile profile) {
        UserProfileResponseDto dto = new UserProfileResponseDto();
        dto.setId(profile.getId());
        dto.setUserId(profile.getUserId());
        dto.setFullName(profile.getFullName());
        dto.setEmail(profile.getEmail());
        dto.setPhone(profile.getPhone());
        dto.setAddress(profile.getAddress());
        dto.setDateOfBirth(profile.getDateOfBirth());
        dto.setGender(profile.getGender());
        dto.setProfilePictureUrl(profile.getProfilePictureUrl());
        dto.setRole(profile.getRole());
        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
