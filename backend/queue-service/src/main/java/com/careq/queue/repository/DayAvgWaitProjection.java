package com.careq.queue.repository;

/**
 * Projection for the average-wait analytics query: a calendar day
 * (yyyy-MM-dd) paired with the average wait in minutes for entries
 * called on that day.
 */
public interface DayAvgWaitProjection {

    String getDay();

    Double getAvgWaitMinutes();
}
