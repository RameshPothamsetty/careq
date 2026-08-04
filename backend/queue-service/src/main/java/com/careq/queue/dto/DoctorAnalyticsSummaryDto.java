package com.careq.queue.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-doctor analytics summary (dashboard upgrade).
 *
 * Aggregated server-side from the {@code queue_entries} table for one
 * doctor's catalog entry. "Today" figures are scalars over the current
 * calendar day; the trends cover the last 7 days (inclusive of today)
 * with missing days zero-filled so charts always render a full window.
 */
public class DoctorAnalyticsSummaryDto {

    private long patientsCompletedToday;
    /** Null when no patient was called today. */
    private Double avgWaitTodayMinutes;
    /** Null when no consultation completed today. */
    private Double avgConsultTimeTodayMinutes;
    private List<DailyPatientCountDto> patientsPerDay = new ArrayList<>();
    private List<DailyAvgWaitDto> avgWaitTimeTrend = new ArrayList<>();

    public DoctorAnalyticsSummaryDto() {
    }

    public long getPatientsCompletedToday() {
        return patientsCompletedToday;
    }

    public void setPatientsCompletedToday(long patientsCompletedToday) {
        this.patientsCompletedToday = patientsCompletedToday;
    }

    public Double getAvgWaitTodayMinutes() {
        return avgWaitTodayMinutes;
    }

    public void setAvgWaitTodayMinutes(Double avgWaitTodayMinutes) {
        this.avgWaitTodayMinutes = avgWaitTodayMinutes;
    }

    public Double getAvgConsultTimeTodayMinutes() {
        return avgConsultTimeTodayMinutes;
    }

    public void setAvgConsultTimeTodayMinutes(Double avgConsultTimeTodayMinutes) {
        this.avgConsultTimeTodayMinutes = avgConsultTimeTodayMinutes;
    }

    public List<DailyPatientCountDto> getPatientsPerDay() {
        return patientsPerDay;
    }

    public void setPatientsPerDay(List<DailyPatientCountDto> patientsPerDay) {
        this.patientsPerDay = patientsPerDay;
    }

    public List<DailyAvgWaitDto> getAvgWaitTimeTrend() {
        return avgWaitTimeTrend;
    }

    public void setAvgWaitTimeTrend(List<DailyAvgWaitDto> avgWaitTimeTrend) {
        this.avgWaitTimeTrend = avgWaitTimeTrend;
    }
}
