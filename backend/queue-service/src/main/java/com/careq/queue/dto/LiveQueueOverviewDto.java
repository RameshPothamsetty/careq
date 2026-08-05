package com.careq.queue.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Admin-only live overview across all doctors (Day 9: fully documented).
 */
@Schema(description = "Hospital-wide live queue overview (Admin).")
public class LiveQueueOverviewDto {

    @Schema(description = "Total patients waiting across all queues", example = "14")
    private int totalWaiting;

    @Schema(description = "Total patients currently in consultation", example = "3")
    private int totalInProgress;

    @Schema(description = "Number of doctors online", example = "8")
    private long doctorsOnline;

    @Schema(description = "Number of doctors offline", example = "2")
    private long doctorsOffline;

    @Schema(description = "Waiting patients whose predicted wait exceeds the delay threshold", example = "2")
    private int delayedConsultations;

    @Schema(description = "Average predicted wait across waiting patients (minutes)", example = "22")
    private int averageWaitMinutes;

    @Schema(description = "Per-doctor breakdown")
    private List<DoctorQueueStatsDto> doctors;

    public LiveQueueOverviewDto() {
    }

    public int getTotalWaiting() {
        return totalWaiting;
    }

    public void setTotalWaiting(int totalWaiting) {
        this.totalWaiting = totalWaiting;
    }

    public int getTotalInProgress() {
        return totalInProgress;
    }

    public void setTotalInProgress(int totalInProgress) {
        this.totalInProgress = totalInProgress;
    }

    public long getDoctorsOnline() {
        return doctorsOnline;
    }

    public void setDoctorsOnline(long doctorsOnline) {
        this.doctorsOnline = doctorsOnline;
    }

    public long getDoctorsOffline() {
        return doctorsOffline;
    }

    public void setDoctorsOffline(long doctorsOffline) {
        this.doctorsOffline = doctorsOffline;
    }

    public int getDelayedConsultations() {
        return delayedConsultations;
    }

    public void setDelayedConsultations(int delayedConsultations) {
        this.delayedConsultations = delayedConsultations;
    }

    public int getAverageWaitMinutes() {
        return averageWaitMinutes;
    }

    public void setAverageWaitMinutes(int averageWaitMinutes) {
        this.averageWaitMinutes = averageWaitMinutes;
    }

    public List<DoctorQueueStatsDto> getDoctors() {
        return doctors;
    }

    public void setDoctors(List<DoctorQueueStatsDto> doctors) {
        this.doctors = doctors;
    }
}
