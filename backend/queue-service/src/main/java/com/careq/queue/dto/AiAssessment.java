package com.careq.queue.dto;

import com.careq.queue.entity.TriageLevel;

/**
 * Result of a single LLM symptom assessment: the clinical urgency level plus
 * the department the AI believes the symptoms point to (null when the model
 * could not pick one, e.g. during the fallback path).
 *
 * Produced by {@link com.careq.queue.client.TriageAiClient#assess} and used by
 * the AI doctor recommendation flow: triage drives ordering, the department
 * drives doctor matching.
 */
public record AiAssessment(TriageLevel triage, String department) {
}
