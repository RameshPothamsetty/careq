package com.careq.queue.entity;

public enum BillStatus {
    /** Created when the consultation completes; awaiting payment. */
    PENDING,
    /** Settled via the sandbox payment flow. */
    PAID
}
