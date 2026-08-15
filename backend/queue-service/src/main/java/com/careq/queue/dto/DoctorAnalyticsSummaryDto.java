package com.careq.queue.dto;

import io.swagger.v3.oas.annotations.media.Schema;

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
@Schema(description = "Per-doctor analytics summary: today's scalars + 7-day trends.")
public class DoctorAnalyticsSummaryDto {

    @Schema(description = "Patients completed today", example = "12")
    private long patientsCompletedToday;

    @Schema(description = "Average wait today in minutes (called_at - joined_at); null when no patient was called", example = "10.5")
    private Double avgWaitTodayMinutes;

    @Schema(description = "Average consultation time today in minutes; null when no consultation completed", example = "14.0")
    private Double avgConsultTimeTodayMinutes;

    @Schema(description = "Patients handled per day, oldest first (zero-filled window)")
    private List<DailyPatientCountDto> patientsPerDay = new ArrayList<>();

    @Schema(description = "Average wait per day, oldest first; null on days with no calls")
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
