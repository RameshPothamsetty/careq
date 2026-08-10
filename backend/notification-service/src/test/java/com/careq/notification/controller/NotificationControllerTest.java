package com.careq.notification.controller;

import com.careq.notification.dto.NotificationListResponse;
import com.careq.notification.dto.NotificationResponseDto;
import com.careq.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoint integration test (@SpringBootTest + MockMvc + H2). The RabbitMQ
 * listener container is switched off by application-test.yml, and the service
 * layer is mocked — this verifies the controller contract (headers → JSON).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @Test
    void getMyNotifications_ReturnsPageWithUnreadCount() throws Exception {
        NotificationResponseDto called = new NotificationResponseDto();
        called.setId(1L);
        called.setType("queue.called");
        called.setMessage("Dr. Arjun Sharma has called you — please head to the consultation room.");
        called.setRead(false);
        called.setCreatedAt(LocalDateTime.of(2026, 8, 10, 9, 0));

        NotificationListResponse page = new NotificationListResponse();
        page.setContent(List.of(called));
        page.setTotalElements(1);
        page.setTotalPages(1);
        page.setNumber(0);
        page.setSize(20);
        page.setFirst(true);
        page.setLast(true);
        page.setEmpty(false);
        page.setUnreadCount(1);

        given(notificationService.getMyNotifications(eq("patient-uuid"), anyInt(), anyInt())).willReturn(page);

        mockMvc.perform(get("/api/notifications/me")
                        .header("X-User-Id", "patient-uuid")
                        .header("X-User-Role", "PATIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("queue.called"))
                .andExpect(jsonPath("$.content[0].read").value(false))
                .andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    void markRead_ReturnsUpdatedNotification() throws Exception {
        NotificationResponseDto read = new NotificationResponseDto();
        read.setId(1L);
        read.setType("queue.called");
        read.setMessage("called");
        read.setRead(true);
        read.setCreatedAt(LocalDateTime.of(2026, 8, 10, 9, 0));

        given(notificationService.markRead(eq(1L), eq("patient-uuid"), eq("PATIENT"))).willReturn(read);

        mockMvc.perform(put("/api/notifications/1/read")
                        .header("X-User-Id", "patient-uuid")
                        .header("X-User-Role", "PATIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.read").value(true));
    }

    @Test
    void getMyNotifications_MissingIdentityHeader_Returns400() throws Exception {
        mockMvc.perform(get("/api/notifications/me"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing Header"));
    }

    @Test
    void unknownRoute_Returns404WithSharedErrorShape() throws Exception {
        mockMvc.perform(get("/api/nonexistent-path"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }
}
