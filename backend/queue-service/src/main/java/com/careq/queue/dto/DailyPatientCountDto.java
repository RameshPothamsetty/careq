package com.careq.queue.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/** One day's count of patients handled (queue entries completed that day). */
@Schema(description = "Patients handled on a single day.")
public class DailyPatientCountDto {

    @Schema(description = "Calendar date", example = "2026-08-04")
    private LocalDate date;

    @Schema(description = "Number of patients completed that day", example = "5")
    private long count;

    public DailyPatientCountDto() {
    }

    public DailyPatientCountDto(LocalDate date, long count) {
        this.date = date;
        this.count = count;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }
}
