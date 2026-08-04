package com.careq.queue.dto;

import java.util.List;

/**
 * Deserialization mirror of Spring Data's serialized Page for the doctor
 * catalog. queue-service only reads {@link #getContent()} — it never
 * persists doctor data (single source of truth stays in doctor-service).
 */
public class DoctorCatalogPageDto {

    private List<DoctorCatalogResponseDto> content;
    private int number;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean last;

    public DoctorCatalogPageDto() {
    }

    public List<DoctorCatalogResponseDto> getContent() {
        return content;
    }

    public void setContent(List<DoctorCatalogResponseDto> content) {
        this.content = content;
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public boolean isLast() {
        return last;
    }

    public void setLast(boolean last) {
        this.last = last;
    }
}
