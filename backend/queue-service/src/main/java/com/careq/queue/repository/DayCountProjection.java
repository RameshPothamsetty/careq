package com.careq.queue.repository;

/**
 * Projection for native analytics queries: a calendar day (yyyy-MM-dd)
 * paired with a count. The day is returned as a String via MySQL's
 * DATE_FORMAT so it needs no JDBC date conversion.
 *
 * IMPORTANT: property names must match the SQL aliases exactly (cnt) —
 * Spring Data maps interface-projection getters to native-query column
 * aliases by name, and a mismatch fails at runtime.
 */
public interface DayCountProjection {

    String getDay();

    Long getCnt();
}
