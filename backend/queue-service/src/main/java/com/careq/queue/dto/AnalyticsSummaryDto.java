package com.careq.queue.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin analytics summary.
 *
 * Aggregated server-side from the {@code queue_entries} table over the
 * last 7 days (inclusive of today), with missing days zero-filled so the
 * frontend charts always render a complete window.
 */
@Schema(description = "Last-7-day analytics summary (Admin).")
public class AnalyticsSummaryDto {

    @Schema(description = "Patients completed per day, oldest first (zero-filled window)")
    private List<DailyPatientCountDto> patientsPerDay = new ArrayList<>();

    @Schema(description = "Average wait per day, oldest first; null on days with no calls")
    private List<DailyAvgWaitDto> avgWaitTimeTrend = new ArrayList<>();

    @Schema(description = "Completed patients grouped by department, sorted by count descending")
    private List<DepartmentDistributionDto> departmentDistribution = new ArrayList<>();

    public AnalyticsSummaryDto() {
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

    public List<DepartmentDistributionDto> getDepartmentDistribution() {
        return departmentDistribution;
    }

    public void setDepartmentDistribution(List<DepartmentDistributionDto> departmentDistribution) {
        this.departmentDistribution = departmentDistribution;
    }
}
