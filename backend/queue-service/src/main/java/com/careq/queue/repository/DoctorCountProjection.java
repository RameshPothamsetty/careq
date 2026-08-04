package com.careq.queue.repository;

/**
 * Projection for the per-doctor count used to build the department
 * distribution: a doctor catalog entry id paired with the number of
 * entries for that doctor in the analytics window.
 */
public interface DoctorCountProjection {

    Long getDoctorCatalogEntryId();

    Long getCount();
}
