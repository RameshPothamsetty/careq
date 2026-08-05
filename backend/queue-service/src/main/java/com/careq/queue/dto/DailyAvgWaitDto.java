package com.careq.queue.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * One day's average wait time in minutes (called_at - joined_at) for
 * entries called on that day (Day 9: fully documented). Null when no
 * patient was called that day.
 */
@Schema(description = "Average patient wait on a single day.")
public class DailyAvgWaitDto {

    @Schema(description = "Calendar date", example = "2026-08-04")
    private LocalDate date;

    @Schema(description = "Average wait in minutes (called_at - joined_at); null when no patient was called", example = "15.0")
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
