package com.careq.queue.integration;

import com.careq.queue.client.DoctorServiceClient;
import com.careq.queue.client.TriageAiClient;
import com.careq.queue.dto.DoctorCatalogResponseDto;
import com.careq.queue.entity.TriageLevel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Day 10 — Flow B integration test (@SpringBootTest + MockMvc + H2).
 *
 * The full patient journey through the real queue stack (controller → service
 * → repository against H2): join → AI triage assigns a level → derived
 * position + predicted wait present → doctor calls next (IN_PROGRESS) →
 * doctor completes (COMPLETED) → the visit lands in the patient's history.
 *
 * Per the Day 10 scope decision, the EXTERNAL calls are mocked at this layer:
 *  - {@link DoctorServiceClient} (Feign) — the doctor-service catalog,
 *  - {@link TriageAiClient} — the Groq LLM call.
 * The real AiTriageService wrapper (fallback-to-NORMAL) still runs on top of
 * the mocked Groq client, and the real database/transactions are exercised.
 *
 * Choice note: H2 (MySQL mode) instead of Testcontainers — the JPA model is
 * portable, the analytics native queries are NOT exercised by these flows,
 * and H2 keeps the suite runnable with zero Docker dependency.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
// Each test rolls back its writes — the shared H2 instance must never leak
// state between tests (without this, two tests using the same PATIENT id
// become order-dependent and flaky across JVMs/CI).
@Transactional
class QueueFlowIntegrationTest {

    private static final String PATIENT = "patient-flow-uuid";
    private static final String DOCTOR_USER = "doctor-flow-uuid";
    private static final Long DOCTOR_CATALOG_ID = 10L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DoctorServiceClient doctorServiceClient;

    @MockBean
    private TriageAiClient triageAiClient;

    @BeforeEach
    void setUp() throws Exception {
        DoctorCatalogResponseDto doctor = new DoctorCatalogResponseDto();
        doctor.setId(DOCTOR_CATALOG_ID);
        doctor.setUserId(DOCTOR_USER);
        doctor.setName("Dr. Flow Test");
        doctor.setDepartmentName("Cardiology");
        doctor.setSpecialization("Interventional Cardiology");
        doctor.setAvgConsultationTimeMinutes(15);
        doctor.setIsAvailable(true);
        given(doctorServiceClient.getDoctorById(DOCTOR_CATALOG_ID)).willReturn(doctor);
        // The Groq client is mocked; the real AiTriageService fallback wrapper still runs.
        given(triageAiClient.classify(anyString())).willReturn(Optional.of(TriageLevel.HIGH));
    }

    @Test
    void flowB_fullLifecycle_joinToCompletedWithWaitTimeAndHistory() throws Exception {
        // 1. Patient joins → 201, AI triage assigned, derived position + wait present.
        MvcResult join = mockMvc.perform(post("/api/queue/join")
                        .header("X-User-Id", PATIENT)
                        .header("X-User-Role", "PATIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"doctorCatalogEntryId\":10,\"patientName\":\"Flow Patient\"," +
                                "\"symptomText\":\"persistent headache with blurred vision\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.aiSuggestedTriage").value("HIGH"))
                .andExpect(jsonPath("$.effectiveTriage").value("HIGH"))
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.predictedWaitMinutes").value(0))
                .andExpect(jsonPath("$.doctorName").value("Dr. Flow Test"))
                .andReturn();

        JsonNode joinBody = objectMapper.readTree(join.getResponse().getContentAsString());
        long entryId = joinBody.get("id").asLong();

        // 2. Patient's own status reports the active entry with a live position.
        mockMvc.perform(get("/api/queue/my-status")
                        .header("X-User-Id", PATIENT)
                        .header("X-User-Role", "PATIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.entry.id").value(entryId))
                .andExpect(jsonPath("$.entry.position").value(1));

        // 3. Doctor sees the patient in their live queue.
        mockMvc.perform(get("/api/queue/doctor/{id}", DOCTOR_CATALOG_ID)
                        .header("X-User-Id", DOCTOR_USER)
                        .header("X-User-Role", "DOCTOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].patientName").value("Flow Patient"));

        // 4. Doctor calls next → IN_PROGRESS with calledAt set.
        mockMvc.perform(put("/api/queue/{id}/call-next", entryId)
                        .header("X-User-Id", DOCTOR_USER)
                        .header("X-User-Role", "DOCTOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.calledAt").isNotEmpty());

        // 5. Doctor completes → COMPLETED with completedAt set.
        mockMvc.perform(put("/api/queue/{id}/complete", entryId)
                        .header("X-User-Id", DOCTOR_USER)
                        .header("X-User-Role", "DOCTOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());

        // 6. The completed visit lands in the patient's history, newest first.
        mockMvc.perform(get("/api/queue/my-history")
                        .header("X-User-Id", PATIENT)
                        .header("X-User-Role", "PATIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(entryId))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));
    }

    @Test
    void join_doctorServiceUnreachable_Returns503NotUnhandled500() throws Exception {
        // Feign failure (timeout / connection refused) must surface as a
        // graceful 503 Service Unavailable, never an unhandled 500.
        given(doctorServiceClient.getDoctorById(DOCTOR_CATALOG_ID))
                .willThrow(new RuntimeException("connection refused"));

        mockMvc.perform(post("/api/queue/join")
                        .header("X-User-Id", PATIENT)
                        .header("X-User-Role", "PATIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"doctorCatalogEntryId\":10,\"symptomText\":\"cough\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("temporarily unavailable")));
    }

    @Test
    void join_samePatientSecondActiveEntry_Returns409() throws Exception {
        mockMvc.perform(post("/api/queue/join")
                        .header("X-User-Id", PATIENT)
                        .header("X-User-Role", "PATIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"doctorCatalogEntryId\":10,\"symptomText\":\"fever\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/queue/join")
                        .header("X-User-Id", PATIENT)
                        .header("X-User-Role", "PATIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"doctorCatalogEntryId\":10,\"symptomText\":\"fever again\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void join_nonPatientRole_Returns403() throws Exception {
        mockMvc.perform(post("/api/queue/join")
                        .header("X-User-Id", DOCTOR_USER)
                        .header("X-User-Role", "DOCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"doctorCatalogEntryId\":10,\"symptomText\":\"cough\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void join_blankSymptomText_Returns400WithValidationErrors() throws Exception {
        mockMvc.perform(post("/api/queue/join")
                        .header("X-User-Id", PATIENT)
                        .header("X-User-Role", "PATIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"doctorCatalogEntryId\":10,\"symptomText\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors").isArray());
    }

    @Test
    void unknownRoute_Returns404WithSharedErrorShape() throws Exception {
        // BUG-1: an unmapped route must be 404, never the catch-all handler's 500.
        mockMvc.perform(get("/api/nonexistent-path"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.path").value("/api/nonexistent-path"));
    }
}
