package com.careq.doctor.service;

import com.careq.doctor.dto.DepartmentRequestDto;
import com.careq.doctor.dto.DepartmentResponseDto;
import com.careq.doctor.entity.Department;
import com.careq.doctor.exception.DepartmentNotFoundException;
import com.careq.doctor.repository.DepartmentRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentRepository departmentRepository;

    public DepartmentServiceImpl(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    // Day 13: cached in Redis (60s TTL) — department names also appear inside
    // the cached doctor list, so department mutations evict BOTH caches.
    @Cacheable(cacheNames = "departments")
    public List<DepartmentResponseDto> getAllDepartments() {
        return departmentRepository.findAll().stream()
                .map(DepartmentResponseDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public DepartmentResponseDto getDepartmentById(Long id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new DepartmentNotFoundException(
                        "Department not found with id: " + id));
        return DepartmentResponseDto.fromEntity(department);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {"departments", "doctorCatalog"}, allEntries = true)
    public DepartmentResponseDto createDepartment(DepartmentRequestDto request) {
        if (departmentRepository.existsByName(request.getName().trim())) {
            throw new IllegalArgumentException(
                    "Department with name '" + request.getName().trim() + "' already exists");
        }

        Department department = new Department(request.getName().trim(), request.getDescription());
        department = departmentRepository.save(department);
        return DepartmentResponseDto.fromEntity(department);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {"departments", "doctorCatalog"}, allEntries = true)
    public DepartmentResponseDto updateDepartment(Long id, DepartmentRequestDto request) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new DepartmentNotFoundException(
                        "Department not found with id: " + id));

        // Check if the new name conflicts with another department
        String newName = request.getName().trim();
        if (!department.getName().equals(newName) && departmentRepository.existsByName(newName)) {
            throw new IllegalArgumentException(
                    "Department with name '" + newName + "' already exists");
        }

        department.setName(newName);
        department.setDescription(request.getDescription());
        department = departmentRepository.save(department);
        return DepartmentResponseDto.fromEntity(department);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {"departments", "doctorCatalog"}, allEntries = true)
    public void deleteDepartment(Long id) {
        if (!departmentRepository.existsById(id)) {
            throw new DepartmentNotFoundException(
                    "Department not found with id: " + id);
        }
        departmentRepository.deleteById(id);
    }
}
