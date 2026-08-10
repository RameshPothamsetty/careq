package com.careq.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Paginated notification response that mirrors Spring Data's Page JSON shape
 * (so the frontend reuses its existing {@code PaginatedResponse} type) plus a
 * total {@code unreadCount} — the badge must reflect ALL unread rows, not just
 * the ones on the current page.
 */
@Schema(description = "Paginated page of the caller's notifications plus a total unread count.")
public class NotificationListResponse {

    private List<NotificationResponseDto> content;
    private long totalElements;
    private int totalPages;
    private int number;
    private int size;
    private boolean first;
    private boolean last;
    private boolean empty;
    private long unreadCount;

    public NotificationListResponse() {
    }

    public static NotificationListResponse fromPage(Page<NotificationResponseDto> page, long unreadCount) {
        NotificationListResponse response = new NotificationListResponse();
        response.setContent(page.getContent());
        response.setTotalElements(page.getTotalElements());
        response.setTotalPages(page.getTotalPages());
        response.setNumber(page.getNumber());
        response.setSize(page.getSize());
        response.setFirst(page.isFirst());
        response.setLast(page.isLast());
        response.setEmpty(page.isEmpty());
        response.setUnreadCount(unreadCount);
        return response;
    }

    public List<NotificationResponseDto> getContent() {
        return content;
    }

    public void setContent(List<NotificationResponseDto> content) {
        this.content = content;
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

    public boolean isFirst() {
        return first;
    }

    public void setFirst(boolean first) {
        this.first = first;
    }

    public boolean isLast() {
        return last;
    }

    public void setLast(boolean last) {
        this.last = last;
    }

    public boolean isEmpty() {
        return empty;
    }

    public void setEmpty(boolean empty) {
        this.empty = empty;
    }

    public long getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(long unreadCount) {
        this.unreadCount = unreadCount;
    }
}
