package com.careq.queue.service;

import com.careq.queue.client.TriageAiClient;
import com.careq.queue.entity.TriageLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * Unit tests for the AI fallback-to-NORMAL behavior (Day 5).
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
    void classify_Success_ReturnsAiLevel() throws Exception {
        given(triageAiClient.classify("chest pain")).willReturn(Optional.of(TriageLevel.EMERGENCY));

        assertThat(aiTriageService.classifyWithFallback("chest pain")).isEqualTo(TriageLevel.EMERGENCY);
    }

    @Test
    void classify_ClientThrows_FallsBackToNormal() throws Exception {
        // Simulate a broken AI call (network error, timeout, bad key, 5xx).
        given(triageAiClient.classify("headache")).willThrow(new RuntimeException("Groq API timeout"));

        assertThat(aiTriageService.classifyWithFallback("headache")).isEqualTo(TriageLevel.NORMAL);
    }

    @Test
    void classify_UnparseableResponse_FallsBackToNormal() throws Exception {
        given(triageAiClient.classify("cough")).willReturn(Optional.empty());

        assertThat(aiTriageService.classifyWithFallback("cough")).isEqualTo(TriageLevel.NORMAL);
    }

    @Test
    void classify_ClientReturnsNull_FallsBackToNormal() throws Exception {
        given(triageAiClient.classify("dizziness")).willReturn(null);

        assertThat(aiTriageService.classifyWithFallback("dizziness")).isEqualTo(TriageLevel.NORMAL);
    }
}
