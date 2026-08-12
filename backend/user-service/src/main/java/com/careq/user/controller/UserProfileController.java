package com.careq.user.controller;

import com.careq.user.dto.UpdateUserProfileRequestDto;
import com.careq.user.dto.UserProfileResponseDto;
import com.careq.user.exception.ErrorResponseDto;
import com.careq.user.exception.RoleGuard;
import com.careq.user.service.FileStorageService;
import com.careq.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.file.Path;

/**
 * User profile endpoints (Day 9: fully documented with OpenAPI).
 *
 * Identity (X-User-Id / X-User-Role) is provided by the API Gateway after
 * JWT validation, so no Bearer token is read inside this service.
 */
@Tag(name = "User Profiles", description = "Profile management for all roles. Requires a valid JWT (Authorize with your token).")
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

    @Operation(summary = "Get my profile",
            description = "Returns the calling user's profile. A default empty profile is lazily created on first access, " +
                    "so this endpoint always succeeds for any authenticated user.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The calling user's profile (lazy-created if missing)",
                    content = @Content(schema = @Schema(implementation = UserProfileResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Missing X-User-Id / X-User-Role header",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    // Day 15 fix: azure-storage-blob transitively pulls jackson-dataformat-xml,
    // which Spring's default converter order prefers over JSON. Declaring
    // produces=application/json keeps every DTO response JSON (the frontend and
    // the gateway contract expect JSON; the image route below is unaffected).
    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserProfileResponseDto> getMyProfile(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Role") String role,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Name", required = false) String fullName,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Email", required = false) String email) {
        UserProfileResponseDto profile = userProfileService.getOrCreateProfile(userId, role, fullName, email);
        return ResponseEntity.ok(profile);
    }

    @Operation(summary = "Update my profile",
            description = "Partially updates the calling user's profile. Only non-null fields are changed; " +
                    "omit fields you do not want to touch.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Updated profile",
                    content = @Content(schema = @Schema(implementation = UserProfileResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed (bad phone/gender) or missing header",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PutMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserProfileResponseDto> updateMyProfile(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody UpdateUserProfileRequestDto request) {
        UserProfileResponseDto profile = userProfileService.updateProfile(userId, request);
        return ResponseEntity.ok(profile);
    }

    @Operation(summary = "Upload my profile picture",
            description = "Uploads an image file (multipart/form-data, image/* only) and stores it server-side. " +
                    "The returned profile carries the picture's public URL.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profile with the new profilePictureUrl",
                    content = @Content(schema = @Schema(implementation = UserProfileResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Empty file, non-image file, or missing header",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PostMapping(value = "/me/profile-picture", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserProfileResponseDto> uploadProfilePicture(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId,
            @Parameter(description = "Image file to upload (jpeg/png/gif/webp)", required = true)
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

    @Operation(summary = "Serve a stored profile picture",
            description = "Public route (whitelisted at the gateway) that streams a stored profile picture by filename.")
    @SecurityRequirements // intentionally overrides the global bearer requirement — images load via <img> tags
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The image file"),
            @ApiResponse(responseCode = "404", description = "File not found")
    })
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

    /**
     * Admin: paginated, searchable user list (by display name or email).
     * Added Day 7a — this was the missing counterpart to GET /api/users/{id}
     * and gives the Admin a real user-management view.
     */
    @Operation(summary = "List all users (Admin only)",
            description = "Paginated list of all user profiles, optionally filtered by a case-insensitive " +
                    "substring match on display name or email. Requires the ADMIN role.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Page of user profiles",
                    content = @Content(schema = @Schema(implementation = Page.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not an ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<UserProfileResponseDto>> getAllUsers(
            @Parameter(description = "Case-insensitive match on fullName or email") @RequestParam(required = false) String search,
            @Parameter(description = "Zero-based page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (clamped to max 100)") @RequestParam(defaultValue = "20") int size,
            @Parameter(hidden = true) @RequestHeader("X-User-Role") String requesterRole) {

        RoleGuard.requireRole(requesterRole, "ADMIN");

        return ResponseEntity.ok(userProfileService.getAllProfiles(search, page, size));
    }

    @Operation(summary = "Get any user's profile (Admin only)",
            description = "Looks up a profile by the target user's UUID. Requires the ADMIN role.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The requested profile",
                    content = @Content(schema = @Schema(implementation = UserProfileResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not an ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "No profile found for the given user id",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserProfileResponseDto> getUserProfile(
            @Parameter(description = "Target user UUID") @PathVariable("id") String targetUserId,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String requesterUserId,
            @Parameter(hidden = true) @RequestHeader("X-User-Role") String requesterRole) {

        RoleGuard.requireRole(requesterRole, "ADMIN");

        UserProfileResponseDto profile = userProfileService.getProfileByUserId(targetUserId);
        return ResponseEntity.ok(profile);
    }
}
