package com.careq.doctor.service;

import com.careq.doctor.dto.AvailabilityRequestDto;
import com.careq.doctor.dto.DoctorCatalogRequestDto;
import com.careq.doctor.dto.DoctorCatalogResponseDto;
import org.springframework.data.domain.Page;

public interface DoctorCatalogService {

    /**
     * Paginated, filtered, sortable doctor listing.
     *
     * @param departmentId   optional filter by department
     * @param specialization optional filter by specialization (case-insensitive substring)
     * @param search         optional free-text search on name OR specialization
     * @param page           zero-based page number (clamped to >= 0)
     * @param size           page size (clamped to the configured maximum)
     * @param sortBy         sort property: name | consultationFee | experienceYears
     * @param sortDirection  asc | desc
     */
    Page<DoctorCatalogResponseDto> getAllDoctors(Long departmentId, String specialization, String search,
                                                 int page, int size, String sortBy, String sortDirection);

    DoctorCatalogResponseDto getDoctorById(Long id);

    DoctorCatalogResponseDto createDoctor(DoctorCatalogRequestDto request);

    DoctorCatalogResponseDto updateDoctor(Long id, DoctorCatalogRequestDto request);

    void deleteDoctor(Long id);

    DoctorCatalogResponseDto toggleAvailability(String userId, AvailabilityRequestDto request);

    /**
     * Day 13: resolves the CALLING doctor's own catalog entry by their userId
     * (header-based identity). Returns 404 when the account is not linked to
     * a catalog entry yet — the frontend uses this instead of scanning the
     * whole paginated catalog, which is fragile once the catalog grows.
     */
    DoctorCatalogResponseDto getMyDoctor(String userId);
}
