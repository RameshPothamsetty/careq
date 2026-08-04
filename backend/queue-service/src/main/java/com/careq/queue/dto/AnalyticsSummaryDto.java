package com.careq.queue.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin analytics summary (Day 7b).
 *
 * Aggregated server-side from the {@code queue_entries} table over the
 * last 7 days (inclusive of today), with missing days zero-filled so the
 * frontend charts always render a complete window.
 */
public class AnalyticsSummaryDto {

    private List<DailyPatientCountDto> patientsPerDay = new ArrayList<>();
    private List<DailyAvgWaitDto> avgWaitTimeTrend = new ArrayList<>();
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
