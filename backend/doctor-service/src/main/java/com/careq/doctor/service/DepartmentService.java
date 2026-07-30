package com.careq.doctor.service;

import com.careq.doctor.dto.DepartmentRequestDto;
import com.careq.doctor.dto.DepartmentResponseDto;

import java.util.List;

public interface DepartmentService {

    List<DepartmentResponseDto> getAllDepartments();

    DepartmentResponseDto getDepartmentById(Long id);

    DepartmentResponseDto createDepartment(DepartmentRequestDto request);

    DepartmentResponseDto updateDepartment(Long id, DepartmentRequestDto request);

    void deleteDepartment(Long id);
}
