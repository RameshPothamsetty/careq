package com.careq.user.service;

import com.careq.user.dto.UpdateUserProfileRequestDto;
import com.careq.user.dto.UserProfileResponseDto;
import com.careq.user.entity.UserProfile;
import com.careq.user.exception.UserProfileNotFoundException;
import com.careq.user.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserProfileServiceImpl} .
 */
@ExtendWith(MockitoExtension.class)
class UserProfileServiceImplTest {

    private static final String TEST_USER_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String TEST_ROLE = "PATIENT";
    private static final String TEST_PHONE = "+1234567890";
    private static final String TEST_ADDRESS = "123 Main St";

    @Mock
    private UserProfileRepository userProfileRepository;

    private UserProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserProfileServiceImpl(userProfileRepository, 100);
    }

    private UserProfile existingProfile() {
        UserProfile profile = new UserProfile(TEST_USER_ID, TEST_ROLE);
        profile.setPhone(TEST_PHONE);
        profile.setAddress(TEST_ADDRESS);
        return profile;
    }

    private UpdateUserProfileRequestDto updateRequest() {
        UpdateUserProfileRequestDto request = new UpdateUserProfileRequestDto();
        request.setPhone(TEST_PHONE);
        request.setAddress("456 New Street");
        request.setDateOfBirth(LocalDate.of(1990, 5, 15));
        request.setGender("MALE");
        request.setProfilePictureUrl("/api/users/profile-pictures/abc.jpg");
        return request;
    }

    @Test
    void getOrCreateProfile_WhenNotExists_CreatesAndReturnsProfile() {
        when(userProfileRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        UserProfileResponseDto response =
                service.getOrCreateProfile(TEST_USER_ID, TEST_ROLE, "John Patient", "john@careq.com");

        assertThat(response.getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(response.getRole()).isEqualTo(TEST_ROLE);
        assertThat(response.getFullName()).isEqualTo("John Patient");
        assertThat(response.getEmail()).isEqualTo("john@careq.com");

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(captor.capture());
        UserProfile saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(saved.getRole()).isEqualTo(TEST_ROLE);
        assertThat(saved.getFullName()).isEqualTo("John Patient");
    }

    @Test
    void getOrCreateProfile_WhenExists_ReturnsExistingProfile() {
        UserProfile existing = existingProfile();
        when(userProfileRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(existing));

        UserProfileResponseDto response = service.getOrCreateProfile(TEST_USER_ID, TEST_ROLE, null, null);

        assertThat(response.getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(response.getPhone()).isEqualTo(TEST_PHONE);
        verify(userProfileRepository, never()).save(any());
    }

    @Test
    void getOrCreateProfile_BackfillsMissingNameAndEmail() {
        // Profile created before the name/email columns existed: only userId + role stored.
        UserProfile stale = new UserProfile(TEST_USER_ID, TEST_ROLE);
        when(userProfileRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stale));
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        UserProfileResponseDto response =
                service.getOrCreateProfile(TEST_USER_ID, TEST_ROLE, "John Patient", "john@careq.com");

        assertThat(response.getFullName()).isEqualTo("John Patient");
        assertThat(response.getEmail()).isEqualTo("john@careq.com");
        verify(userProfileRepository).save(any(UserProfile.class));
    }

    @Test
    void updateProfile_WhenExists_UpdatesFieldsAndReturns() {
        UserProfile existing = existingProfile();
        when(userProfileRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(existing));
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        UserProfileResponseDto response = service.updateProfile(TEST_USER_ID, updateRequest());

        assertThat(response.getPhone()).isEqualTo(TEST_PHONE);
        assertThat(response.getAddress()).isEqualTo("456 New Street");
        assertThat(response.getDateOfBirth()).isEqualTo(LocalDate.of(1990, 5, 15));
        assertThat(response.getGender()).isEqualTo("MALE");
        assertThat(response.getProfilePictureUrl()).isEqualTo("/api/users/profile-pictures/abc.jpg");
    }

    @Test
    void updateProfile_WhenNotExists_ThrowsException() {
        when(userProfileRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateProfile(TEST_USER_ID, updateRequest()))
                .isInstanceOf(UserProfileNotFoundException.class);
    }

    @Test
    void updateProfile_PartialUpdate_OnlyUpdatesNonNullFields() {
        UserProfile existing = existingProfile();
        existing.setGender("FEMALE");
        when(userProfileRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(existing));
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateUserProfileRequestDto request = new UpdateUserProfileRequestDto();
        request.setAddress(TEST_ADDRESS);
        request.setGender("other"); // lower-case input must be normalized to OTHER

        UserProfileResponseDto response = service.updateProfile(TEST_USER_ID, request);

        assertThat(response.getAddress()).isEqualTo(TEST_ADDRESS);
        assertThat(response.getPhone()).isEqualTo(TEST_PHONE);   // untouched
        assertThat(response.getDateOfBirth()).isNull();          // untouched
        assertThat(response.getGender()).isEqualTo("OTHER");     // normalized
    }

    @Test
    void getProfileByUserId_WhenExists_ReturnsProfile() {
        when(userProfileRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(existingProfile()));

        UserProfileResponseDto response = service.getProfileByUserId(TEST_USER_ID);

        assertThat(response.getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(response.getPhone()).isEqualTo(TEST_PHONE);
    }

    @Test
    void getProfileByUserId_WhenNotExists_ThrowsException() {
        when(userProfileRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProfileByUserId(TEST_USER_ID))
                .isInstanceOf(UserProfileNotFoundException.class);
    }
}
