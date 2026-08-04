package com.careq.doctor.controller;

import com.careq.doctor.dto.DepartmentRequestDto;
import com.careq.doctor.dto.DepartmentResponseDto;
import com.careq.doctor.exception.RoleGuard;
import com.careq.doctor.service.DepartmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    public ResponseEntity<List<DepartmentResponseDto>> getAllDepartments() {
        return ResponseEntity.ok(departmentService.getAllDepartments());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DepartmentResponseDto> getDepartmentById(@PathVariable Long id) {
        return ResponseEntity.ok(departmentService.getDepartmentById(id));
    }

    @PostMapping
    public ResponseEntity<DepartmentResponseDto> createDepartment(
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody DepartmentRequestDto request) {

        RoleGuard.requireRole(role, "ADMIN");

        DepartmentResponseDto response = departmentService.createDepartment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DepartmentResponseDto> updateDepartment(
            @RequestHeader("X-User-Role") String role,
            @PathVariable Long id,
            @Valid @RequestBody DepartmentRequestDto request) {

        RoleGuard.requireRole(role, "ADMIN");

        return ResponseEntity.ok(departmentService.updateDepartment(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDepartment(
            @RequestHeader("X-User-Role") String role,
            @PathVariable Long id) {

        RoleGuard.requireRole(role, "ADMIN");

        departmentService.deleteDepartment(id);
        return ResponseEntity.noContent().build();
    }
}
