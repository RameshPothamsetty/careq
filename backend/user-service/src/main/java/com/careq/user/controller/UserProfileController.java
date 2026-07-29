package com.careq.user.controller;

import com.careq.user.dto.UpdateUserProfileRequestDto;
import com.careq.user.dto.UserProfileResponseDto;
import com.careq.user.exception.UnauthorizedException;
import com.careq.user.service.FileStorageService;
import com.careq.user.service.UserProfileService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/users")
public class UserProfileController {

    private final UserProfileService userProfileService;
    private final FileStorageService fileStorageService;

    public UserProfileController(UserProfileService userProfileService,
                                  FileStorageService fileStorageService) {
        this.userProfileService = userProfileService;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponseDto> getMyProfile(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role) {
        UserProfileResponseDto profile = userProfileService.getOrCreateProfile(userId, role);
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/me")
    public ResponseEntity<UserProfileResponseDto> updateMyProfile(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody UpdateUserProfileRequestDto request) {
        UserProfileResponseDto profile = userProfileService.updateProfile(userId, request);
        return ResponseEntity.ok(profile);
    }

    @PostMapping("/me/profile-picture")
    public ResponseEntity<UserProfileResponseDto> uploadProfilePicture(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role,
            @RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        // Validate file type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Only image files are allowed");
        }

        // Store the file
        String fileUrl = fileStorageService.storeFile(file, userId);

        // Update the profile with the new picture URL
        UpdateUserProfileRequestDto updateRequest = new UpdateUserProfileRequestDto();
        updateRequest.setProfilePictureUrl(fileUrl);

        UserProfileResponseDto profile = userProfileService.updateProfile(userId, updateRequest);
        return ResponseEntity.ok(profile);
    }

    @GetMapping("/profile-pictures/{filename:.+}")
    public ResponseEntity<Resource> serveProfilePicture(@PathVariable String filename) {
        Path filePath = fileStorageService.getFilePath(filename);

        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                // Determine content type
                String contentType = "image/jpeg";
                if (filename.toLowerCase().endsWith(".png")) {
                    contentType = "image/png";
                } else if (filename.toLowerCase().endsWith(".gif")) {
                    contentType = "image/gif";
                } else if (filename.toLowerCase().endsWith(".webp")) {
                    contentType = "image/webp";
                }

                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }

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
