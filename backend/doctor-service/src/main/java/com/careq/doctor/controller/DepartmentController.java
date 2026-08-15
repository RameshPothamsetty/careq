package com.careq.doctor.controller;

import com.careq.doctor.dto.DepartmentRequestDto;
import com.careq.doctor.dto.DepartmentResponseDto;
import com.careq.doctor.exception.ErrorResponseDto;
import com.careq.doctor.exception.RoleGuard;
import com.careq.doctor.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Department endpoints (fully documented with OpenAPI).
 */
@Tag(name = "Departments", description = "Hospital department catalog. Listing is open to any authenticated role; " +
        "create/update/delete require ADMIN.")
@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @Operation(summary = "List all departments",
            description = "Returns every department (active and inactive). Any authenticated role may call this.")
    @ApiResponse(responseCode = "200", description = "List of departments",
            content = @Content(schema = @Schema(implementation = DepartmentResponseDto.class)))
    @GetMapping
    public ResponseEntity<List<DepartmentResponseDto>> getAllDepartments() {
        return ResponseEntity.ok(departmentService.getAllDepartments());
    }

    @Operation(summary = "Get a department by ID",
            description = "Returns a single department. Any authenticated role may call this.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The department",
                    content = @Content(schema = @Schema(implementation = DepartmentResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Department not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<DepartmentResponseDto> getDepartmentById(@PathVariable Long id) {
        return ResponseEntity.ok(departmentService.getDepartmentById(id));
    }

    @Operation(summary = "Create a department (Admin only)",
            description = "Creates a department with a unique name. Requires the ADMIN role.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Department created",
                    content = @Content(schema = @Schema(implementation = DepartmentResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed or duplicate name",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not an ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PostMapping
    public ResponseEntity<DepartmentResponseDto> createDepartment(
            @Parameter(hidden = true) @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody DepartmentRequestDto request) {

        RoleGuard.requireRole(role, "ADMIN");

        DepartmentResponseDto response = departmentService.createDepartment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Update a department (Admin only)",
            description = "Updates an existing department's name/description. Requires the ADMIN role.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Updated department",
                    content = @Content(schema = @Schema(implementation = DepartmentResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed or duplicate name",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not an ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Department not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PutMapping("/{id}")
    public ResponseEntity<DepartmentResponseDto> updateDepartment(
            @Parameter(hidden = true) @RequestHeader("X-User-Role") String role,
            @PathVariable Long id,
            @Valid @RequestBody DepartmentRequestDto request) {

        RoleGuard.requireRole(role, "ADMIN");

        return ResponseEntity.ok(departmentService.updateDepartment(id, request));
    }

    @Operation(summary = "Delete a department (Admin only)",
            description = "Deletes a department by ID. Requires the ADMIN role.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Deleted; no content"),
            @ApiResponse(responseCode = "403", description = "Caller is not an ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Department not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDepartment(
            @Parameter(hidden = true) @RequestHeader("X-User-Role") String role,
            @PathVariable Long id) {

        RoleGuard.requireRole(role, "ADMIN");

        departmentService.deleteDepartment(id);
        return ResponseEntity.noContent().build();
    }
}
