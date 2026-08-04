package com.careq.queue.dto;

import java.time.LocalDate;

/**
 * One day's average wait time in minutes (called_at - joined_at) for
 * entries called on that day. Null when no patient was called that day.
 */
public class DailyAvgWaitDto {

    private LocalDate date;
    private Double avgWaitMinutes;

    public DailyAvgWaitDto() {
    }

    public DailyAvgWaitDto(LocalDate date, Double avgWaitMinutes) {
        this.date = date;
        this.avgWaitMinutes = avgWaitMinutes;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public Double getAvgWaitMinutes() {
        return avgWaitMinutes;
    }

    public void setAvgWaitMinutes(Double avgWaitMinutes) {
        this.avgWaitMinutes = avgWaitMinutes;
    }
}
