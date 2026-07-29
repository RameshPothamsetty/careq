package com.careq.user.service;

import com.careq.user.dto.UpdateUserProfileRequestDto;
import com.careq.user.dto.UserProfileResponseDto;

public interface UserProfileService {

    /**
     * Retrieves the profile for the given userId.
     * If no profile exists yet, creates a default empty profile (lazy creation)
     * and returns it. This pattern avoids coupling auth-service signup to
     * user-service profile creation.
     *
     * @param userId the user's ID from the auth-service (via X-User-Id header)
     * @param role   the user's role from the JWT (via X-User-Role header)
     * @return the user's profile (newly created or existing)
     */
    UserProfileResponseDto getOrCreateProfile(String userId, String role);

    /**
     * Updates the profile for the given userId.
     *
     * @param userId  the user's ID from the auth-service
     * @param request the fields to update
     * @return the updated profile
     */
    UserProfileResponseDto updateProfile(String userId, UpdateUserProfileRequestDto request);

    /**
     * Retrieves any user's profile by userId. Admin-only access.
     *
     * @param userId the target user's ID
     * @return the user's profile
     * @throws com.careq.user.exception.UserProfileNotFoundException if profile not found
     */
    UserProfileResponseDto getProfileByUserId(String userId);
}
