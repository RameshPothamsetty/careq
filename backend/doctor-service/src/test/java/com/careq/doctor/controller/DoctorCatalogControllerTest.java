package com.careq.doctor.controller;

import com.careq.doctor.dto.DoctorCatalogResponseDto;
import com.careq.doctor.exception.GlobalExceptionHandler;
import com.careq.doctor.service.DoctorCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
 * Standalone MockMvc tests for {@link DoctorCatalogController} (Day 4 plan,
 * built Day 10). Identity headers are simulated directly — the gateway is
 * what normally injects them after JWT validation.
 */
@ExtendWith(MockitoExtension.class)
class DoctorCatalogControllerTest {

    @Mock
    private DoctorCatalogService doctorCatalogService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DoctorCatalogController controller = new DoctorCatalogController(doctorCatalogService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private DoctorCatalogResponseDto doctor() {
        DoctorCatalogResponseDto dto = new DoctorCatalogResponseDto();
        dto.setId(1L);
        dto.setName("Dr. Arjun Sharma");
        dto.setUserId("550e8400-e29b-41d4-a716-446655440001");
        dto.setDepartmentId(1L);
        dto.setDepartmentName("Cardiology");
        dto.setSpecialization("Interventional Cardiology");
        dto.setQualification("MD, DM Cardiology");
        dto.setExperienceYears(12);
        dto.setConsultationFee(new BigDecimal("500.00"));
        dto.setAvgConsultationTimeMinutes(15);
        dto.setIsAvailable(true);
        return dto;
    }

    private static final String VALID_BODY = "{\"name\":\"Dr. Arjun Sharma\",\"userId\":\"u-1\","
            + "\"departmentId\":1,\"specialization\":\"Cardiology\",\"qualification\":\"MD\","
            + "\"experienceYears\":12,\"consultationFee\":500.00,\"avgConsultationTimeMinutes\":15}";

    @Test
    void getAllDoctors_ShouldReturn200() throws Exception {
        // PageImpl(list) would use Pageable.unpaged(), whose getOffset() throws on
        // serialization — mirror production by using a real PageRequest.
        Page<DoctorCatalogResponseDto> page =
                new PageImpl<>(List.of(doctor()), PageRequest.of(0, 20), 1);
        when(doctorCatalogService.getAllDoctors(any(), any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/doctors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Dr. Arjun Sharma"));
    }

    @Test
    void getDoctorById_ShouldReturn200() throws Exception {
        when(doctorCatalogService.getDoctorById(1L)).thenReturn(doctor());

        mockMvc.perform(get("/api/doctors/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentName").value("Cardiology"));
    }

    @Test
    void createDoctor_AsAdmin_ShouldReturn201() throws Exception {
        when(doctorCatalogService.createDoctor(any())).thenReturn(doctor());

        mockMvc.perform(post("/api/doctors")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Dr. Arjun Sharma"));
    }

    @Test
    void createDoctor_AsNonAdmin_ShouldReturn403() throws Exception {
        mockMvc.perform(post("/api/doctors")
                        .header("X-User-Role", "PATIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void toggleAvailability_AsDoctor_ShouldReturn200() throws Exception {
        when(doctorCatalogService.toggleAvailability(any(), any())).thenReturn(doctor());

        mockMvc.perform(put("/api/doctors/me/availability")
                        .header("X-User-Id", "550e8400-e29b-41d4-a716-446655440001")
                        .header("X-User-Role", "DOCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isAvailable\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAvailable").value(true));
    }

    @Test
    void toggleAvailability_AsNonDoctor_ShouldReturn403() throws Exception {
        mockMvc.perform(put("/api/doctors/me/availability")
                        .header("X-User-Id", "some-id")
                        .header("X-User-Role", "PATIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isAvailable\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteDoctor_AsAdmin_ShouldReturn204() throws Exception {
        doNothing().when(doctorCatalogService).deleteDoctor(anyLong());

        mockMvc.perform(delete("/api/doctors/{id}", 1L)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isNoContent());
    }
}
