package com.careq.user.service;

import com.careq.user.dto.UpdateUserProfileRequestDto;
import com.careq.user.dto.UserProfileResponseDto;
import com.careq.user.entity.UserProfile;
import com.careq.user.exception.UserProfileNotFoundException;
import com.careq.user.repository.UserProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileServiceImpl implements UserProfileService {

    private final UserProfileRepository userProfileRepository;

    public UserProfileServiceImpl(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    /**
     * Lazy profile creation: on first access, if no profile row exists for this userId,
     * create a default empty profile with just the userId and role.
     * This keeps auth-service completely untouched — no need to call user-service
     * during signup.
     */
    @Override
    @Transactional
    public UserProfileResponseDto getOrCreateProfile(String userId, String role) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserProfile newProfile = new UserProfile(userId, role);
                    return userProfileRepository.save(newProfile);
                });
        return UserProfileResponseDto.fromEntity(profile);
    }

    @Override
    @Transactional
    public UserProfileResponseDto updateProfile(String userId, UpdateUserProfileRequestDto request) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new UserProfileNotFoundException(
                        "No profile found for user: " + userId));

        if (request.getPhone() != null) {
            profile.setPhone(request.getPhone());
        }
        if (request.getAddress() != null) {
            profile.setAddress(request.getAddress());
        }
        if (request.getDateOfBirth() != null) {
            profile.setDateOfBirth(request.getDateOfBirth());
        }
        if (request.getGender() != null) {
            profile.setGender(request.getGender().toUpperCase());
        }
        if (request.getProfilePictureUrl() != null) {
            profile.setProfilePictureUrl(request.getProfilePictureUrl());
        }

        profile = userProfileRepository.save(profile);
        return UserProfileResponseDto.fromEntity(profile);
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponseDto getProfileByUserId(String userId) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new UserProfileNotFoundException(
                        "No profile found for user: " + userId));
        return UserProfileResponseDto.fromEntity(profile);
    }
}
