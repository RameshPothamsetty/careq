package com.careq.queue.client;

import com.careq.queue.dto.AiAssessment;

import java.util.List;
import java.util.Optional;

/**
 * Abstraction over the LLM symptom-assessment call.
 *
 * Implementations contact an LLM provider (currently Groq) and return the
 * suggested urgency level plus the most likely department for the symptoms.
 * Returning {@link Optional#empty()} means the model responded but its answer
 * could not be parsed. Any communication/parsing error is signaled by throwing
 * an exception.
 *
 * Callers (AiTriageService) treat both cases as "fall back to NORMAL".
 */
public interface TriageAiClient {

    /**
     * @param symptomText        free-text symptoms described by the patient
     * @param allowedDepartments canonical department names the model may pick from
     *                           (may be empty — then no department is requested)
     * @return the suggested triage level + department, or empty if unparseable
     * @throws Exception on any failure to reach or parse the LLM response
     */
    Optional<AiAssessment> assess(String symptomText, List<String> allowedDepartments) throws Exception;
}
