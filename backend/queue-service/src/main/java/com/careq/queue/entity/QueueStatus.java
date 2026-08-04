package com.careq.queue.entity;

/**
 * Lifecycle state of a queue entry.
 *
 * WAITING      — joined the queue, not yet called
 * IN_PROGRESS  — called by the doctor, consultation underway
 * COMPLETED    — consultation finished
 * CANCELLED    — left the queue before being seen (future/patient-initiated)
 */
public enum QueueStatus {
    WAITING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
