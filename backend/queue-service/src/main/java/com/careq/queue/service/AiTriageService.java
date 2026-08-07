package com.careq.queue.service;

import com.careq.queue.client.TriageAiClient;
import com.careq.queue.dto.AiAssessment;
import com.careq.queue.entity.TriageLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Wraps the LLM assessment call with the fallback-to-NORMAL policy.
 *
 * A broken AI call must NEVER be a hard failure for a real patient trying to
 * see a doctor. If the LLM call throws, times out, or returns something
 * unparseable, we log the failure and fall back to {@link TriageLevel#NORMAL}
 * with no department suggestion.
 *
 * Isolated behind {@link TriageAiClient} so the fallback logic can be
 * unit-tested with a mocked AI client.
 */
@Service
public class AiTriageService {

    private static final Logger log = LoggerFactory.getLogger(AiTriageService.class);

    private final TriageAiClient triageAiClient;

    public AiTriageService(TriageAiClient triageAiClient) {
        this.triageAiClient = triageAiClient;
    }

    /**
     * Assesses symptom text via the LLM (urgency + likely department),
     * degrading gracefully to NORMAL / no department on any failure.
     *
     * @param symptomText        free-text symptoms
     * @param allowedDepartments canonical department names the model may pick from
     * @return an AiAssessment that is never null; NORMAL with a null department on failure
     */
    public AiAssessment assessWithFallback(String symptomText, List<String> allowedDepartments) {
        try {
            Optional<AiAssessment> result = triageAiClient.assess(symptomText, allowedDepartments);
            if (result != null && result.isPresent()) {
                return result.get();
            }
            log.warn("AI assessment returned no parseable result for symptoms — falling back to NORMAL");
            return new AiAssessment(TriageLevel.NORMAL, null);
        } catch (Exception e) {
            log.warn("AI assessment call failed — falling back to NORMAL. Reason: {}", e.getMessage());
            return new AiAssessment(TriageLevel.NORMAL, null);
        }
    }

    /**
     * Classifies symptom text via the LLM, degrading gracefully to NORMAL.
     * Kept for the join-queue path, which only needs the urgency level.
     *
     * @return a TriageLevel that is never null; NORMAL on any failure
     */
    public TriageLevel classifyWithFallback(String symptomText) {
        return assessWithFallback(symptomText, List.of()).triage();
    }
}
