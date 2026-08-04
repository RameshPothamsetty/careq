package com.careq.queue.repository;

/**
 * Projection for native analytics queries: a calendar day (yyyy-MM-dd)
 * paired with a count. The day is returned as a String via MySQL's
 * DATE_FORMAT so it needs no JDBC date conversion.
 */
public interface DayCountProjection {

    String getDay();

    Long getCount();
}
