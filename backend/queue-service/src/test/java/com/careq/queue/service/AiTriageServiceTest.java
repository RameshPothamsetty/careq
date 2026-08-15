package com.careq.queue.service;

import com.careq.queue.client.TriageAiClient;
import com.careq.queue.dto.AiAssessment;
import com.careq.queue.entity.TriageLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

/**
 * Unit tests for the AI fallback-to-NORMAL behavior .
 * The AI client is mocked to simulate both success and failure.
 */
@ExtendWith(MockitoExtension.class)
class AiTriageServiceTest {

    @Mock
    private TriageAiClient triageAiClient;

    private AiTriageService aiTriageService;

    @BeforeEach
    void setUp() {
        aiTriageService = new AiTriageService(triageAiClient);
    }

    @Test
    void assess_Success_ReturnsTriageAndDepartment() throws Exception {
        given(triageAiClient.assess("chest pain", List.of("Cardiology")))
                .willReturn(Optional.of(new AiAssessment(TriageLevel.EMERGENCY, "Cardiology")));

        AiAssessment result = aiTriageService.assessWithFallback("chest pain", List.of("Cardiology"));

        assertThat(result.triage()).isEqualTo(TriageLevel.EMERGENCY);
        assertThat(result.department()).isEqualTo("Cardiology");
    }

    @Test
    void assess_ClientThrows_FallsBackToNormal() throws Exception {
        // Simulate a broken AI call (network error, timeout, bad key, 5xx).
        given(triageAiClient.assess(eq("headache"), anyList()))
                .willThrow(new RuntimeException("Groq API timeout"));

        AiAssessment result = aiTriageService.assessWithFallback("headache", List.of("Neurology"));

        assertThat(result.triage()).isEqualTo(TriageLevel.NORMAL);
        assertThat(result.department()).isNull();
    }

    @Test
    void assess_UnparseableResponse_FallsBackToNormal() throws Exception {
        given(triageAiClient.assess(eq("cough"), anyList())).willReturn(Optional.empty());

        AiAssessment result = aiTriageService.assessWithFallback("cough", List.of());

        assertThat(result.triage()).isEqualTo(TriageLevel.NORMAL);
        assertThat(result.department()).isNull();
    }

    @Test
    void assess_ClientReturnsNull_FallsBackToNormal() throws Exception {
        given(triageAiClient.assess(eq("dizziness"), anyList())).willReturn(null);

        AiAssessment result = aiTriageService.assessWithFallback("dizziness", List.of());

        assertThat(result.triage()).isEqualTo(TriageLevel.NORMAL);
        assertThat(result.department()).isNull();
    }

    @Test
    void classify_DelegatesToAssess_ReturnsLevelOnly() throws Exception {
        given(triageAiClient.assess("chest pain", List.of()))
                .willReturn(Optional.of(new AiAssessment(TriageLevel.EMERGENCY, null)));

        assertThat(aiTriageService.classifyWithFallback("chest pain")).isEqualTo(TriageLevel.EMERGENCY);
    }
}
