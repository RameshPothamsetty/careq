package com.careq.queue.dto;

import java.time.LocalDate;

/** One day's count of patients handled (queue entries completed that day). */
public class DailyPatientCountDto {

    private LocalDate date;
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
