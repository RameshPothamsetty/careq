package com.careq.notification.service;

import com.careq.notification.dto.NotificationListResponse;
import com.careq.notification.dto.NotificationResponseDto;
import com.careq.notification.entity.NotificationEntry;
import com.careq.notification.exception.NotificationNotFoundException;
import com.careq.notification.exception.UnauthorizedException;
import com.careq.notification.repository.NotificationEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Service-layer tests for notification persistence + ownership rules (Day 13).
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    private static final String PATIENT = "patient-uuid";
    private static final String ADMIN = "admin-uuid";

    @Mock
    private NotificationEntryRepository repository;

    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationServiceImpl(repository, 50);
    }

    @Test
    void getMyNotifications_ReturnsOnlyCallersRowsWithUnreadCount() {
        NotificationEntry called = new NotificationEntry(PATIENT, "queue.called",
                "Dr. Arjun Sharma has called you — please head to the consultation room.");
        called.setRead(false);
        NotificationEntry joined = new NotificationEntry(PATIENT, "queue.joined",
                "You joined Dr. Arjun Sharma's queue — position #3.");
        joined.setRead(true);

        given(repository.findByRecipientUserIdOrderByCreatedAtDesc(eq(PATIENT), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(called, joined)));
        given(repository.countByRecipientUserIdAndReadFalse(PATIENT)).willReturn(1L);

        NotificationListResponse response = notificationService.getMyNotifications(PATIENT, 0, 20);

        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getContent().get(0).getType()).isEqualTo("queue.called");
        assertThat(response.getContent().get(0).isRead()).isFalse();
        assertThat(response.getContent().get(1).isRead()).isTrue();
        assertThat(response.getUnreadCount()).isEqualTo(1);
    }

    @Test
    void getMyNotifications_ClampsPageSizeToConfiguredMaximum() {
        given(repository.findByRecipientUserIdOrderByCreatedAtDesc(eq(PATIENT), any(Pageable.class)))
                .willReturn(Page.empty());
        given(repository.countByRecipientUserIdAndReadFalse(PATIENT)).willReturn(0L);

        notificationService.getMyNotifications(PATIENT, 0, 999);

        verify(repository).findByRecipientUserIdOrderByCreatedAtDesc(eq(PATIENT),
                org.mockito.ArgumentMatchers.argThat(pageable -> pageable.getPageSize() == 50));
    }

    @Test
    void markRead_RecipientMarksOwnNotification() {
        NotificationEntry entry = new NotificationEntry(PATIENT, "queue.called", "called");
        entry.setRead(false);
        given(repository.findById(1L)).willReturn(Optional.of(entry));
        given(repository.save(any(NotificationEntry.class))).willAnswer(inv -> inv.getArgument(0));

        NotificationResponseDto response = notificationService.markRead(1L, PATIENT, "PATIENT");

        assertThat(response.isRead()).isTrue();
        verify(repository).save(entry);
    }

    @Test
    void markRead_AdminCanMarkAnyNotification() {
        NotificationEntry entry = new NotificationEntry(PATIENT, "queue.joined", "joined");
        entry.setRead(false);
        given(repository.findById(1L)).willReturn(Optional.of(entry));
        given(repository.save(any(NotificationEntry.class))).willAnswer(inv -> inv.getArgument(0));

        NotificationResponseDto response = notificationService.markRead(1L, ADMIN, "ADMIN");

        assertThat(response.isRead()).isTrue();
    }

    @Test
    void markRead_AnotherUserCannotMarkOthersNotification() {
        NotificationEntry entry = new NotificationEntry(PATIENT, "queue.joined", "joined");
        given(repository.findById(1L)).willReturn(Optional.of(entry));

        assertThatThrownBy(() -> notificationService.markRead(1L, "someone-else", "PATIENT"))
                .isInstanceOf(UnauthorizedException.class);
        verify(repository, never()).save(any(NotificationEntry.class));
    }

    @Test
    void markRead_NotFound_Throws() {
        given(repository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markRead(99L, PATIENT, "PATIENT"))
                .isInstanceOf(NotificationNotFoundException.class);
    }

    @Test
    void markRead_AlreadyRead_IsIdempotent() {
        NotificationEntry entry = new NotificationEntry(PATIENT, "queue.completed", "completed");
        entry.setRead(true);
        given(repository.findById(1L)).willReturn(Optional.of(entry));

        NotificationResponseDto response = notificationService.markRead(1L, PATIENT, "PATIENT");

        assertThat(response.isRead()).isTrue();
        // No save — nothing changed.
        verify(repository, never()).save(any(NotificationEntry.class));
    }
}
