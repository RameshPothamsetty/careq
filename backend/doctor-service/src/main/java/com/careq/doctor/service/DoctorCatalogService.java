package com.careq.doctor.service;

import com.careq.doctor.dto.AvailabilityRequestDto;
import com.careq.doctor.dto.DoctorCatalogRequestDto;
import com.careq.doctor.dto.DoctorCatalogResponseDto;

import java.util.List;

public interface DoctorCatalogService {

    List<DoctorCatalogResponseDto> getAllDoctors(Long departmentId, String specialization);

    DoctorCatalogResponseDto getDoctorById(Long id);

    DoctorCatalogResponseDto createDoctor(DoctorCatalogRequestDto request);

    DoctorCatalogResponseDto updateDoctor(Long id, DoctorCatalogRequestDto request);

    void deleteDoctor(Long id);

    DoctorCatalogResponseDto toggleAvailability(String userId, AvailabilityRequestDto request);
}
