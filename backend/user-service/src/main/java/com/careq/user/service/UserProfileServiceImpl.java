package com.careq.user.service;

import com.careq.user.dto.UpdateUserProfileRequestDto;
import com.careq.user.dto.UserProfileResponseDto;
import com.careq.user.entity.UserProfile;
import com.careq.user.exception.UserProfileNotFoundException;
import com.careq.user.repository.UserProfileRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileServiceImpl implements UserProfileService {

    private final UserProfileRepository userProfileRepository;

    /** Upper bound for the admin user-list page size. */
    private final int maxPageSize;

    public UserProfileServiceImpl(UserProfileRepository userProfileRepository,
                                  @Value("${user-list.max-page-size:100}") int maxPageSize) {
        this.userProfileRepository = userProfileRepository;
        this.maxPageSize = maxPageSize;
    }

    /**
     * Lazy profile creation: on first access, if no profile row exists for this userId,
     * create a default empty profile with just the userId and role.
     * This keeps auth-service completely untouched — no need to call user-service
     * during signup.
     */
    @Override
    @Transactional
    public UserProfileResponseDto getOrCreateProfile(String userId, String role, String fullName, String email) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserProfile newProfile = new UserProfile(userId, role);
                    // Display name/email come from the JWT claims (forwarded by
                    // the gateway as X-User-Name/X-User-Email) — stored here so
                    // the Admin user list has something to search and show.
                    newProfile.setFullName(fullName);
                    newProfile.setEmail(email);
                    return userProfileRepository.save(newProfile);
                });

        // Backfill display fields for profiles created before the name/email columns existed (they only
        // stored userId + role). The JWT claims are authoritative for name/email,
        // so this self-heals on the next /me access after a fresh login.
        boolean changed = false;
        if (fullName != null && !fullName.isBlank()
                && (profile.getFullName() == null || profile.getFullName().isBlank())) {
            profile.setFullName(fullName);
            changed = true;
        }
        if (email != null && !email.isBlank()
                && (profile.getEmail() == null || profile.getEmail().isBlank())) {
            profile.setEmail(email);
            changed = true;
        }
        if (changed) {
            profile = userProfileRepository.save(profile);
        }
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

    @Override
    @Transactional(readOnly = true)
    public Page<UserProfileResponseDto> getAllProfiles(String search, int page, int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), maxPageSize),
                Sort.by(Sort.Direction.ASC, "fullName"));
        String trimmed = search == null ? null : search.trim();
        return userProfileRepository.search(trimmed, pageable)
                .map(UserProfileResponseDto::fromEntity);
    }
}
