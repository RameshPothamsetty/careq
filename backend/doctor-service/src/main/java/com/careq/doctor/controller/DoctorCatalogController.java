package com.careq.doctor.controller;

import com.careq.doctor.dto.AvailabilityRequestDto;
import com.careq.doctor.dto.DoctorCatalogRequestDto;
import com.careq.doctor.dto.DoctorCatalogResponseDto;
import com.careq.doctor.exception.RoleGuard;
import com.careq.doctor.service.DoctorCatalogService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    @GetMapping
    public ResponseEntity<Page<DoctorCatalogResponseDto>> getAllDoctors(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String specialization,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection) {
        return ResponseEntity.ok(doctorCatalogService.getAllDoctors(
                departmentId, specialization, search, page, size, sortBy, sortDirection));
    }

    /**
     * Public/Patient: Get a single doctor's catalog entry by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<DoctorCatalogResponseDto> getDoctorById(@PathVariable Long id) {
        return ResponseEntity.ok(doctorCatalogService.getDoctorById(id));
    }

    /**
     * Admin: Create a new doctor catalog entry.
     */
    @PostMapping
    public ResponseEntity<DoctorCatalogResponseDto> createDoctor(
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody DoctorCatalogRequestDto request) {

        RoleGuard.requireRole(role, "ADMIN");

        DoctorCatalogResponseDto response = doctorCatalogService.createDoctor(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Admin: Update a doctor catalog entry.
     */
    @PutMapping("/{id}")
    public ResponseEntity<DoctorCatalogResponseDto> updateDoctor(
            @RequestHeader("X-User-Role") String role,
            @PathVariable Long id,
            @Valid @RequestBody DoctorCatalogRequestDto request) {

        RoleGuard.requireRole(role, "ADMIN");

        return ResponseEntity.ok(doctorCatalogService.updateDoctor(id, request));
    }

    /**
     * Admin: Delete a doctor catalog entry.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDoctor(
            @RequestHeader("X-User-Role") String role,
            @PathVariable Long id) {

        RoleGuard.requireRole(role, "ADMIN");

        doctorCatalogService.deleteDoctor(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Doctor: Toggle own availability status.
     * Looks up the doctor by their userId from the header, not by catalog id.
     */
    @PutMapping("/me/availability")
    public ResponseEntity<DoctorCatalogResponseDto> toggleAvailability(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody AvailabilityRequestDto request) {

        RoleGuard.requireRole(role, "DOCTOR");

        return ResponseEntity.ok(doctorCatalogService.toggleAvailability(userId, request));
    }
}
