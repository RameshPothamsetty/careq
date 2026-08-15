package com.careq.user.controller;

import com.careq.user.dto.UserProfileResponseDto;
import com.careq.user.exception.GlobalExceptionHandler;
import com.careq.user.service.FileStorageService;
import com.careq.user.service.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc tests for {@link UserProfileController} . Identity headers are simulated directly — the gateway is
 * what normally injects them.
 */
@ExtendWith(MockitoExtension.class)
class UserProfileControllerTest {

    private static final String TEST_USER_ID = "550e8400-e29b-41d4-a716-446655440000";

    @Mock
    private UserProfileService userProfileService;

    @Mock
    private FileStorageService fileStorageService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UserProfileController controller = new UserProfileController(userProfileService, fileStorageService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private UserProfileResponseDto profile() {
        UserProfileResponseDto dto = new UserProfileResponseDto();
        dto.setUserId(TEST_USER_ID);
        dto.setFullName("John Patient");
        dto.setEmail("john@careq.com");
        dto.setRole("PATIENT");
        return dto;
    }

    @Test
    void getMyProfile_WithoutHeaders_ShouldReturn400() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMyProfile_WithValidHeaders_ShouldReturn200() throws Exception {
        when(userProfileService.getOrCreateProfile(anyString(), anyString(), any(), any()))
                .thenReturn(profile());

        mockMvc.perform(get("/api/users/me")
                        .header("X-User-Id", TEST_USER_ID)
                        .header("X-User-Role", "PATIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(TEST_USER_ID))
                .andExpect(jsonPath("$.role").value("PATIENT"));
    }

    @Test
    void updateMyProfile_WithValidPayload_ShouldReturn200() throws Exception {
        when(userProfileService.updateProfile(anyString(), any())).thenReturn(profile());

        mockMvc.perform(put("/api/users/me")
                        .header("X-User-Id", TEST_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+1234567890\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(TEST_USER_ID));
    }

    @Test
    void getUserProfile_AsAdmin_ShouldReturn200() throws Exception {
        when(userProfileService.getProfileByUserId(TEST_USER_ID)).thenReturn(profile());

        mockMvc.perform(get("/api/users/{id}", TEST_USER_ID)
                        .header("X-User-Id", "admin-id")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("John Patient"));
    }

    @Test
    void getUserProfile_AsPatient_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/users/{id}", TEST_USER_ID)
                        .header("X-User-Id", "patient-id")
                        .header("X-User-Role", "PATIENT"))
                .andExpect(status().isForbidden());
    }
}
