package com.careq.user.dto;

import com.careq.user.entity.UserProfile;
import java.time.LocalDate;

public class UserProfileResponseDto {

    private Long id;
    private String userId;
    private String fullName;
    private String email;
    private String phone;
    private String address;
    private LocalDate dateOfBirth;
    private String gender;
    private String profilePictureUrl;
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
