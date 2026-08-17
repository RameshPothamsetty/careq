package com.careq.queue.service;

import com.careq.queue.client.DoctorServiceClient;
import com.careq.queue.dto.ChatRequestDto;
import com.careq.queue.dto.ChatResponseDto;
import com.careq.queue.dto.DoctorCatalogPageDto;
import com.careq.queue.dto.DoctorCatalogResponseDto;
import com.careq.queue.dto.DoctorSuggestionDto;
import com.careq.queue.dto.DoctorSuggestionResponseDto;
import com.careq.queue.dto.QueueEntryResponseDto;
import com.careq.queue.dto.QueueStatusResponseDto;
import com.careq.queue.entity.TriageLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * Chat assistant intent engine — greeting/help/status/departments/doctor
 * lookups must route to the right live-data answer, and unknown input falls
 * back without inventing data.
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    private static final String PATIENT = "patient-uuid";

    @Mock
    private DoctorRecommendationService recommendationService;
    @Mock
    private QueueService queueService;
    @Mock
    private DoctorServiceClient doctorServiceClient;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(recommendationService, queueService, doctorServiceClient);
    }

    private ChatRequestDto message(String text) {
        ChatRequestDto request = new ChatRequestDto();
        request.setMessage(text);
        return request;
    }

    @Test
    void chat_Greeting_ReturnsWelcome() {
        ChatResponseDto response = chatService.chat(PATIENT, message("hello"));

        assertThat(response.getIntent()).isEqualTo("GREETING");
        assertThat(response.getReply()).contains("CareQ assistant");
    }

    @Test
    void chat_Help_ReturnsCapabilities() {
        ChatResponseDto response = chatService.chat(PATIENT, message("what can you do?"));

        assertThat(response.getIntent()).isEqualTo("HELP");
        assertThat(response.getReply()).contains("queue status");
    }

    @Test
    void chat_QueueStatus_ActiveEntry_ReportsPositionAndWait() {
        QueueEntryResponseDto entry = new QueueEntryResponseDto();
        entry.setId(1L);
        entry.setStatus(com.careq.queue.entity.QueueStatus.WAITING);
        entry.setPosition(3);
        entry.setPredictedWaitMinutes(20);
        entry.setDoctorName("Dr. Cardio");
        entry.setDepartmentName("Cardiology");
        given(queueService.getMyStatus(PATIENT)).willReturn(new QueueStatusResponseDto(true, entry));

        ChatResponseDto response = chatService.chat(PATIENT, message("what is my queue status?"));

        assertThat(response.getIntent()).isEqualTo("QUEUE_STATUS");
        assertThat(response.getReply()).contains("#3");
        assertThat(response.getReply()).contains("Dr. Cardio");
        assertThat(response.getReply()).contains("20");
    }

    @Test
    void chat_QueueStatus_Called_ReportsTurn() {
        QueueEntryResponseDto entry = new QueueEntryResponseDto();
        entry.setId(1L);
        entry.setStatus(com.careq.queue.entity.QueueStatus.IN_PROGRESS);
        entry.setDoctorName("Dr. Cardio");
        given(queueService.getMyStatus(PATIENT)).willReturn(new QueueStatusResponseDto(true, entry));

        ChatResponseDto response = chatService.chat(PATIENT, message("my turn?"));

        assertThat(response.getIntent()).isEqualTo("QUEUE_STATUS");
        assertThat(response.getReply()).contains("called you");
    }

    @Test
    void chat_QueueStatus_NoActiveEntry_ReportsNotInQueue() {
        given(queueService.getMyStatus(PATIENT)).willReturn(new QueueStatusResponseDto(false, null));

        ChatResponseDto response = chatService.chat(PATIENT, message("my position"));

        assertThat(response.getIntent()).isEqualTo("QUEUE_STATUS");
        assertThat(response.getReply()).contains("not in a queue");
    }

    @Test
    void chat_Departments_ListsCatalog() {
        DoctorCatalogPageDto page = new DoctorCatalogPageDto();
        page.setContent(List.of(doctor("Cardiology"), doctor("Neurology")));
        given(doctorServiceClient.getAllDoctors(0, 1000)).willReturn(page);

        ChatResponseDto response = chatService.chat(PATIENT, message("which departments do you have?"));

        assertThat(response.getIntent()).isEqualTo("DEPARTMENTS");
        assertThat(response.getReply()).contains("Cardiology");
        assertThat(response.getReply()).contains("Neurology");
    }

    @Test
    void chat_DoctorLookup_ReturnsRankedSuggestions() {
        DoctorSuggestionDto suggestion = new DoctorSuggestionDto();
        suggestion.setDoctorCatalogEntryId(10L);
        suggestion.setName("Dr. Neuro");
        suggestion.setDepartmentName("Neurology");
        suggestion.setPredictedWaitMinutes(5);
        DoctorSuggestionResponseDto ranked = new DoctorSuggestionResponseDto();
        ranked.setTriageLevel(TriageLevel.NORMAL);
        ranked.setEmergency(false);
        ranked.setSuggestedDepartment("Neurology");
        ranked.setSuggestions(List.of(suggestion));
        given(recommendationService.recommend("persistent headache")).willReturn(ranked);

        ChatResponseDto response = chatService.chat(PATIENT, message("which doctor should I see for persistent headache?"));

        assertThat(response.getIntent()).isEqualTo("FIND_DOCTOR");
        assertThat(response.getReply()).contains("Dr. Neuro");
        assertThat(response.getSuggestions()).hasSize(1);
        assertThat(response.getSuggestedDepartment()).isEqualTo("Neurology");
    }

    @Test
    void chat_DoctorLookup_Emergency_WarnsToSeekCareNow() {
        DoctorSuggestionResponseDto ranked = new DoctorSuggestionResponseDto();
        ranked.setTriageLevel(TriageLevel.EMERGENCY);
        ranked.setEmergency(true);
        ranked.setSuggestions(List.of());
        given(recommendationService.recommend("severe chest pain")).willReturn(ranked);

        ChatResponseDto response = chatService.chat(PATIENT, message("doctor for severe chest pain"));

        assertThat(response.getIntent()).isEqualTo("FIND_DOCTOR");
        assertThat(response.getReply()).contains("immediate attention");
    }

    @Test
    void chat_Unknown_ReturnsFriendlyFallback() {
        ChatResponseDto response = chatService.chat(PATIENT, message("asdfgh"));

        assertThat(response.getIntent()).isEqualTo("FALLBACK");
        assertThat(response.getReply()).contains("I'm not sure");
    }

    private DoctorCatalogResponseDto doctor(String department) {
        DoctorCatalogResponseDto d = new DoctorCatalogResponseDto();
        d.setId(1L);
        d.setName("Dr. X");
        d.setDepartmentName(department);
        d.setIsAvailable(true);
        return d;
    }
}
