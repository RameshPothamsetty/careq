package com.careq.queue.client;

import com.careq.queue.entity.TriageLevel;

import java.util.Optional;

/**
 * Abstraction over the LLM symptom-triage call.
 *
 * Implementations contact an LLM provider (currently Groq) and return the
 * suggested TriageLevel. Returning {@link Optional#empty()} means the model
 * responded but its answer could not be parsed into a valid TriageLevel.
 * Any communication/parsing error is signaled by throwing an exception.
 *
 * Callers (AiTriageService) treat both cases as "fall back to NORMAL".
 */
public interface TriageAiClient {

    /**
     * @param symptomText free-text symptoms described by the patient
     * @return the suggested triage level, or empty if unparseable
     * @throws Exception on any failure to reach or parse the LLM response
     */
    Optional<TriageLevel> classify(String symptomText) throws Exception;
}
