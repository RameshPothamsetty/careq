package com.careq.queue.entity;

/**
 * Clinical urgency level assigned to a queue entry.
 *
 * The priority order used for queue ordering is:
 * EMERGENCY > HIGH > NORMAL > FOLLOW_UP
 *
 * aiSuggestedTriage comes from the LLM; doctorOverrideTriage (when set)
 * replaces it and is final. See QueueEntry.effectiveTriage().
 */
public enum TriageLevel {

    EMERGENCY(4),
    HIGH(3),
    NORMAL(2),
    FOLLOW_UP(1);

    private final int priority;

    TriageLevel(int priority) {
        this.priority = priority;
    }

    public int getPriority() {
        return priority;
    }
}
