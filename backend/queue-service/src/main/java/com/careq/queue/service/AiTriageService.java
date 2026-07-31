package com.careq.queue.service;

import com.careq.queue.client.TriageAiClient;
import com.careq.queue.entity.TriageLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Wraps the LLM triage call with the fallback-to-NORMAL policy.
 *
 * A broken AI call must NEVER be a hard failure for a real patient trying to
 * see a doctor. If the LLM call throws, times out, or returns something
 * unparseable, we log the failure and fall back to {@link TriageLevel#NORMAL}.
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
     * Classifies symptom text via the LLM, degrading gracefully to NORMAL.
     *
     * @return a TriageLevel that is never null; NORMAL on any failure
     */
    public TriageLevel classifyWithFallback(String symptomText) {
        try {
            Optional<TriageLevel> result = triageAiClient.classify(symptomText);
            if (result != null && result.isPresent()) {
                return result.get();
            }
            log.warn("AI triage returned no parseable level for symptoms — falling back to NORMAL");
            return TriageLevel.NORMAL;
        } catch (Exception e) {
            log.warn("AI triage call failed — falling back to NORMAL. Reason: {}", e.getMessage());
            return TriageLevel.NORMAL;
        }
    }
}
