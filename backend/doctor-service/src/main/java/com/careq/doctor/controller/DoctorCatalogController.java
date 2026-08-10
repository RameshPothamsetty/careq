package com.careq.doctor.controller;

import com.careq.doctor.dto.AvailabilityRequestDto;
import com.careq.doctor.dto.DoctorCatalogRequestDto;
import com.careq.doctor.dto.DoctorCatalogResponseDto;
import com.careq.doctor.exception.ErrorResponseDto;
import com.careq.doctor.exception.RoleGuard;
import com.careq.doctor.service.DoctorCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Doctor catalog endpoints (Day 9: fully documented with OpenAPI).
 */
@Tag(name = "Doctor Catalog", description = "Doctor catalog and availability. Browsing is open to any authenticated role; " +
        "create/update/delete require ADMIN; availability toggling is restricted to the doctor's own record.")
@RestController
@RequestMapping("/api/doctors")
public class DoctorCatalogController {

    private final DoctorCatalogService doctorCatalogService;

    public DoctorCatalogController(DoctorCatalogService doctorCatalogService) {
        this.doctorCatalogService = doctorCatalogService;
    }

    /**
     * Public/Patient: Paginated, sortable doctor listing with optional filters
     * (department, specialization) and a free-text search on name/specialization.
     * Response shape: { content, totalElements, totalPages, ... } (Spring Page).
     */
    @Operation(summary = "List doctors (paginated, searchable, sortable)",
            description = "Paginated doctor listing with optional departmentId / specialization filters and a free-text " +
                    "search on name or specialization. Sortable by name, consultationFee or experienceYears.")
    @ApiResponse(responseCode = "200", description = "Page of doctor catalog entries",
            content = @Content(schema = @Schema(implementation = DoctorCatalogResponseDto.class)))
    @GetMapping
    public ResponseEntity<Page<DoctorCatalogResponseDto>> getAllDoctors(
            @Parameter(description = "Filter by department ID") @RequestParam(required = false) Long departmentId,
            @Parameter(description = "Filter by specialization (partial, case-insensitive)") @RequestParam(required = false) String specialization,
            @Parameter(description = "Free-text search on name OR specialization") @RequestParam(required = false) String search,
            @Parameter(description = "Zero-based page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (clamped to max 100)") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field: name, consultationFee or experienceYears") @RequestParam(defaultValue = "name") String sortBy,
            @Parameter(description = "Sort direction: asc or desc") @RequestParam(defaultValue = "asc") String sortDirection) {
        return ResponseEntity.ok(doctorCatalogService.getAllDoctors(
                departmentId, specialization, search, page, size, sortBy, sortDirection));
    }

    /**
     * Public/Patient: Get a single doctor's catalog entry by ID.
     */
    @Operation(summary = "Get a doctor by catalog ID",
            description = "Returns a single doctor catalog entry with department name and live availability.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The doctor catalog entry",
                    content = @Content(schema = @Schema(implementation = DoctorCatalogResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Doctor catalog entry not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<DoctorCatalogResponseDto> getDoctorById(@PathVariable Long id) {
        return ResponseEntity.ok(doctorCatalogService.getDoctorById(id));
    }

    /**
     * Admin: Create a new doctor catalog entry.
     */
    @Operation(summary = "Create a doctor catalog entry (Admin only)",
            description = "Creates a doctor catalog entry linked to a department and user. Requires the ADMIN role.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Doctor catalog entry created",
                    content = @Content(schema = @Schema(implementation = DoctorCatalogResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed, duplicate userId, or unknown department",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not an ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PostMapping
    public ResponseEntity<DoctorCatalogResponseDto> createDoctor(
            @Parameter(hidden = true) @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody DoctorCatalogRequestDto request) {

        RoleGuard.requireRole(role, "ADMIN");

        DoctorCatalogResponseDto response = doctorCatalogService.createDoctor(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Admin: Update a doctor catalog entry.
     */
    @Operation(summary = "Update a doctor catalog entry (Admin only)",
            description = "Updates an existing doctor catalog entry. Requires the ADMIN role.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Updated doctor catalog entry",
                    content = @Content(schema = @Schema(implementation = DoctorCatalogResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed or unknown department",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not an ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Doctor catalog entry not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PutMapping("/{id}")
    public ResponseEntity<DoctorCatalogResponseDto> updateDoctor(
            @Parameter(hidden = true) @RequestHeader("X-User-Role") String role,
            @PathVariable Long id,
            @Valid @RequestBody DoctorCatalogRequestDto request) {

        RoleGuard.requireRole(role, "ADMIN");

        return ResponseEntity.ok(doctorCatalogService.updateDoctor(id, request));
    }

    /**
     * Admin: Delete a doctor catalog entry.
     */
    @Operation(summary = "Delete a doctor catalog entry (Admin only)",
            description = "Deletes a doctor catalog entry by ID. Requires the ADMIN role.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Deleted; no content"),
            @ApiResponse(responseCode = "403", description = "Caller is not an ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Doctor catalog entry not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDoctor(
            @Parameter(hidden = true) @RequestHeader("X-User-Role") String role,
            @PathVariable Long id) {

        RoleGuard.requireRole(role, "ADMIN");

        doctorCatalogService.deleteDoctor(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Doctor: Toggle own availability status.
     * Looks up the doctor by their userId from the header, not by catalog id.
     */
    @Operation(summary = "Toggle my availability (Doctor only)",            description = "Sets the calling doctor's availability. The catalog entry is looked up from the caller's " +
                    "userId, so a doctor can only toggle their own record. Requires the DOCTOR role.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Updated availability",
                    content = @Content(schema = @Schema(implementation = DoctorCatalogResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not a DOCTOR",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "No catalog entry found for the caller",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PutMapping("/me/availability")
    public ResponseEntity<DoctorCatalogResponseDto> toggleAvailability(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody AvailabilityRequestDto request) {

        RoleGuard.requireRole(role, "DOCTOR");

        return ResponseEntity.ok(doctorCatalogService.toggleAvailability(userId, request));
    }

    /**
     * Day 13: Doctor: resolve my own catalog entry by the caller's userId.
     * Removes the frontend's need to scan the whole paginated catalog to find
     * "which entry is mine" — a 404 here means the account isn't linked to a
     * catalog entry yet, which the UI turns into a friendly setup state.
     */
    @Operation(summary = "Get my catalog entry (Doctor only)",
            description = "Resolves the calling doctor's own catalog entry from their userId " +
                    "(header-based identity). 404 when the account is not linked to a catalog entry yet.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The caller's catalog entry",
                    content = @Content(schema = @Schema(implementation = DoctorCatalogResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not a DOCTOR",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "No catalog entry found for the caller",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @GetMapping("/me")
    public ResponseEntity<DoctorCatalogResponseDto> getMyDoctor(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Role") String role) {

        RoleGuard.requireRole(role, "DOCTOR");

        return ResponseEntity.ok(doctorCatalogService.getMyDoctor(userId));
    }
}
