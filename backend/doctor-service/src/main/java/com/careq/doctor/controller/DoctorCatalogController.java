package com.careq.doctor.controller;

import com.careq.doctor.dto.AvailabilityRequestDto;
import com.careq.doctor.dto.DoctorCatalogRequestDto;
import com.careq.doctor.dto.DoctorCatalogResponseDto;
import com.careq.doctor.service.DoctorCatalogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/doctors")
public class DoctorCatalogController {

    private final DoctorCatalogService doctorCatalogService;

    public DoctorCatalogController(DoctorCatalogService doctorCatalogService) {
        this.doctorCatalogService = doctorCatalogService;
    }

    /**
     * Public/Patient: List all doctors with optional filtering by department and specialization.
     */
    @GetMapping
    public ResponseEntity<List<DoctorCatalogResponseDto>> getAllDoctors(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String specialization) {
        return ResponseEntity.ok(doctorCatalogService.getAllDoctors(departmentId, specialization));
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

        if (!"ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

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

        if (!"ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(doctorCatalogService.updateDoctor(id, request));
    }

    /**
     * Admin: Delete a doctor catalog entry.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDoctor(
            @RequestHeader("X-User-Role") String role,
            @PathVariable Long id) {

        if (!"ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

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

        if (!"DOCTOR".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(doctorCatalogService.toggleAvailability(userId, request));
    }
}
