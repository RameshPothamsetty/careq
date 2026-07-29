package com.careq.user.controller;

import com.careq.user.dto.UpdateUserProfileRequestDto;
import com.careq.user.dto.UserProfileResponseDto;
import com.careq.user.exception.UnauthorizedException;
import com.careq.user.service.UserProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    /**
     * GET /api/users/me
     * Returns the caller's own profile.
     * If no profile exists yet, lazily creates a default empty profile.
     * The userId and role are extracted from the X-User-Id and X-User-Role
     * headers set by the API Gateway's JWT validation filter.
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponseDto> getMyProfile(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role) {

        UserProfileResponseDto profile = userProfileService.getOrCreateProfile(userId, role);
        return ResponseEntity.ok(profile);
    }

    /**
     * PUT /api/users/me
     * Updates the caller's own profile.
     */
    @PutMapping("/me")
    public ResponseEntity<UserProfileResponseDto> updateMyProfile(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody UpdateUserProfileRequestDto request) {

        UserProfileResponseDto profile = userProfileService.updateProfile(userId, request);
        return ResponseEntity.ok(profile);
    }

    /**
     * GET /api/users/{id}
     * Admin-only: view any user's profile by their userId.
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserProfileResponseDto> getUserProfile(
            @PathVariable("id") String targetUserId,
            @RequestHeader("X-User-Id") String requesterUserId,
            @RequestHeader("X-User-Role") String requesterRole) {

        if (!"ADMIN".equalsIgnoreCase(requesterRole)) {
            throw new UnauthorizedException("Only administrators can view other users' profiles");
        }

        UserProfileResponseDto profile = userProfileService.getProfileByUserId(targetUserId);
        return ResponseEntity.ok(profile);
    }
}
