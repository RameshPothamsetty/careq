package com.careq.queue.dto;

import java.util.List;

/**
 * Admin-only live overview across all doctors.
 */
public class LiveQueueOverviewDto {

    private int totalWaiting;
    private int totalInProgress;
    private long doctorsOnline;
    private long doctorsOffline;
    private int delayedConsultations;
    private int averageWaitMinutes;
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
