package com.careq.notification.service;

import com.careq.notification.dto.NotificationListResponse;
import com.careq.notification.dto.NotificationResponseDto;
import com.careq.notification.entity.NotificationEntry;
import com.careq.notification.exception.NotificationNotFoundException;
import com.careq.notification.exception.UnauthorizedException;
import com.careq.notification.repository.NotificationEntryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationEntryRepository repository;

    /** Upper bound for page size, so a client can never request unbounded pages. */
    private final int maxPageSize;

    public NotificationServiceImpl(NotificationEntryRepository repository,
                                   @Value("${notifications.max-page-size:50}") int maxPageSize) {
        this.repository = repository;
        this.maxPageSize = maxPageSize;
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationListResponse getMyNotifications(String recipientUserId, int page, int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), maxPageSize),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<NotificationResponseDto> content = repository
                .findByRecipientUserIdOrderByCreatedAtDesc(recipientUserId, pageable)
                .map(NotificationResponseDto::fromEntity);
        long unread = repository.countByRecipientUserIdAndReadFalse(recipientUserId);
        return NotificationListResponse.fromPage(content, unread);
    }

    @Override
    @Transactional
    public NotificationResponseDto markRead(Long id, String requesterUserId, String requesterRole) {
        NotificationEntry entry = repository.findById(id)
                .orElseThrow(() -> new NotificationNotFoundException("Notification not found with id: " + id));

        // Only the recipient (or an admin) can mark a notification read —
        // a doctor must never flip another patient's read state.
        if (!"ADMIN".equalsIgnoreCase(requesterRole) && !entry.getRecipientUserId().equals(requesterUserId)) {
            throw new UnauthorizedException("You can only mark your own notifications as read");
        }

        if (!entry.isRead()) {
            entry.setRead(true);
            entry = repository.save(entry);
        }
        return NotificationResponseDto.fromEntity(entry);
    }
}
