package com.careq.doctor.controller;

import com.careq.doctor.dto.DepartmentResponseDto;
import com.careq.doctor.exception.GlobalExceptionHandler;
import com.careq.doctor.service.DepartmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc tests for {@link DepartmentController} (Day 4 plan,
 * built Day 10). The X-User-Role header is simulated directly — the gateway
 * is what normally injects it after JWT validation.
 */
@ExtendWith(MockitoExtension.class)
class DepartmentControllerTest {

    @Mock
    private DepartmentService departmentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DepartmentController controller = new DepartmentController(departmentService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private DepartmentResponseDto department() {
        DepartmentResponseDto dto = new DepartmentResponseDto();
        dto.setId(1L);
        dto.setName("Cardiology");
        dto.setDescription("Heart and cardiovascular system");
        dto.setIsActive(true);
        return dto;
    }

    @Test
    void getAllDepartments_ShouldReturn200() throws Exception {
        when(departmentService.getAllDepartments()).thenReturn(List.of(department()));

        mockMvc.perform(get("/api/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Cardiology"));
    }

    @Test
    void createDepartment_AsAdmin_ShouldReturn201() throws Exception {
        when(departmentService.createDepartment(any())).thenReturn(department());

        mockMvc.perform(post("/api/departments")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cardiology\",\"description\":\"Heart\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Cardiology"));
    }

    @Test
    void createDepartment_AsNonAdmin_ShouldReturn403() throws Exception {
        mockMvc.perform(post("/api/departments")
                        .header("X-User-Role", "PATIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cardiology\",\"description\":\"Heart\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateDepartment_AsAdmin_ShouldReturn200() throws Exception {
        when(departmentService.updateDepartment(anyLong(), any())).thenReturn(department());

        mockMvc.perform(put("/api/departments/{id}", 1L)
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cardiology\",\"description\":\"Heart\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cardiology"));
    }

    @Test
    void deleteDepartment_AsAdmin_ShouldReturn204() throws Exception {
        doNothing().when(departmentService).deleteDepartment(anyLong());

        mockMvc.perform(delete("/api/departments/{id}", 1L)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isNoContent());
    }
}
