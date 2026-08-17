package com.careq.queue.service;

import com.careq.queue.client.DoctorServiceClient;
import com.careq.queue.dto.ChatRequestDto;
import com.careq.queue.dto.ChatResponseDto;
import com.careq.queue.dto.DoctorCatalogResponseDto;
import com.careq.queue.dto.DoctorSuggestionDto;
import com.careq.queue.dto.DoctorSuggestionResponseDto;
import com.careq.queue.dto.QueueStatusResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * CareQ AI chat assistant (heuristic intent engine).
 *
 * <p>A conversational front end over the existing intelligence: it detects
 * the patient's intent from free text and answers from live data — queue
 * status (via {@link QueueService}), doctor recommendations (via
 * {@link DoctorRecommendationService}) and the department catalog. There is no
 * separate LLM call: the doctor-ranking pipeline already includes one when
 * GROQ_API_KEY is configured and degrades to keyword matching otherwise, so
 * the assistant inherits the same graceful-degradation contract.
 *
 * <p>Honesty guardrail: the assistant never invents data. Unknown intents get
 * a friendly fallback that lists what it CAN do; symptom answers carry the
 * emergency flag from triage so urgent cases are told to seek care now.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    /** Cap on how many suggestions a reply carries. */
    private static final int SUGGESTION_LIMIT = 3;

    private final DoctorRecommendationService recommendationService;
    private final QueueService queueService;
    private final DoctorServiceClient doctorServiceClient;

    public ChatService(DoctorRecommendationService recommendationService,
                       QueueService queueService,
                       DoctorServiceClient doctorServiceClient) {
        this.recommendationService = recommendationService;
        this.queueService = queueService;
        this.doctorServiceClient = doctorServiceClient;
    }

    @Transactional(readOnly = true)
    public ChatResponseDto chat(String userId, ChatRequestDto request) {
        String message = request.getMessage().trim();
        String lower = message.toLowerCase(Locale.ROOT);

        if (isGreeting(lower)) {
            return greetingReply();
        }
        if (isHelp(lower)) {
            return helpReply();
        }
        if (isQueueStatus(lower)) {
            return queueStatusReply(userId);
        }
        if (isDepartmentList(lower)) {
            return departmentListReply();
        }
        if (isDoctorLookup(lower)) {
            // Strip the leading "doctor/which doctor" phrasing and treat the
            // rest as symptom text for the ranking pipeline.
            String symptoms = stripDoctorLookupPhrasing(message);
            return doctorLookupReply(symptoms);
        }
        return fallbackReply();
    }

    // ─────────────────────────────────────────────────────────────
    // Intent detection
    // ─────────────────────────────────────────────────────────────

    private boolean isGreeting(String lower) {
        return lower.matches("^(hi|hii+|hello|hey|namaste|good (morning|afternoon|evening))[\\s!.]*$")
                || lower.equals("yo");
    }

    private boolean isHelp(String lower) {
        return lower.contains("help") || lower.contains("what can you do")
                || lower.contains("how do i use") || lower.equals("?");

    }

    private boolean isQueueStatus(String lower) {
        return lower.contains("my status") || lower.contains("my queue")
                || lower.contains("my position") || lower.contains("where am i")
                || lower.contains("how long") || lower.contains("wait time")
                || lower.contains("waiting time") || lower.contains("my turn")
                || (lower.contains("status") && lower.contains("queue"))
                || lower.contains("position");
    }

    private boolean isDepartmentList(String lower) {
        return lower.contains("department") || lower.contains("specialt")
                || lower.contains("which doctors") || lower.contains("list of doctors")
                || lower.contains("what departments");
    }

    private boolean isDoctorLookup(String lower) {
        return lower.contains("doctor") || lower.contains("specialist")
                || lower.contains("see a") || lower.contains("consult")
                || lower.contains("which department") || lower.contains("symptoms")
                || lower.contains("symptom") || lower.contains("should i see")
                || lower.contains("ache") || lower.contains("pain") || lower.contains("fever")
                || lower.contains("cough") || lower.contains("headache") || lower.contains("rash");
    }

    private String stripDoctorLookupPhrasing(String message) {
        return message.replaceAll("(?i)^(i want to |i need to |please |can you |could you |help me )?", "")
                .replaceAll("(?i)(which doctor should i see for|which doctor for|which specialist for|find me a doctor for|recommend a doctor for|see a doctor for|doctor for|doctor)", "")
                .replaceAll("^\\s*(for|with)\\s+", "")
                .replaceAll("[?.!]+$", "")
                .trim();
    }

    // ─────────────────────────────────────────────────────────────
    // Replies
    // ─────────────────────────────────────────────────────────────

    private ChatResponseDto greetingReply() {
        ChatResponseDto response = new ChatResponseDto();
        response.setIntent("GREETING");
        response.setReply("Hi! I'm the CareQ assistant. I can check your queue position, " +
                "suggest a doctor for your symptoms, or list our departments. What do you need?");
        return response;
    }

    private ChatResponseDto helpReply() {
        ChatResponseDto response = new ChatResponseDto();
        response.setIntent("HELP");
        response.setReply("Here's what I can help with:\n" +
                "• \"What's my queue status?\" — your live position and wait\n" +
                "• \"Which doctor for <symptoms>?\" — ranked specialist suggestions\n" +
                "• \"Which departments are available?\" — the full department list\n" +
                "I'm automated, so for emergencies please head to the nearest emergency room right away.");
        return response;
    }

    private ChatResponseDto queueStatusReply(String userId) {
        QueueStatusResponseDto status = queueService.getMyStatus(userId);
        ChatResponseDto response = new ChatResponseDto();
        response.setIntent("QUEUE_STATUS");
        if (!status.isActive() || status.getEntry() == null) {
            response.setReply("You're not in a queue right now. Browse doctors or describe your symptoms " +
                    "and I'll suggest who to see.");
            return response;
        }
        var entry = status.getEntry();
        String doctor = entry.getDoctorName() == null ? "your doctor" : entry.getDoctorName();
        if (com.careq.queue.entity.QueueStatus.IN_PROGRESS.equals(entry.getStatus())) {
            response.setReply(doctor + " has called you — please head to the consultation room now!");
        } else {
            response.setReply("You're #" + entry.getPosition() + " in " + doctor + "'s queue (" +
                    entry.getDepartmentName() + ") — estimated wait ≈ " + entry.getPredictedWaitMinutes() + " min.");
        }
        return response;
    }

    private ChatResponseDto departmentListReply() {
        ChatResponseDto response = new ChatResponseDto();
        response.setIntent("DEPARTMENTS");
        try {
            List<String> departments = doctorServiceClient.getAllDoctors(0, 1000).getContent().stream()
                    .map(DoctorCatalogResponseDto::getDepartmentName)
                    .filter(name -> name != null && !name.isBlank())
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());
            if (departments.isEmpty()) {
                response.setReply("Our department catalog is being updated — try again in a moment.");
            } else {
                response.setReply("We currently have these departments: " + String.join(", ", departments) +
                        ". Tell me your symptoms and I'll suggest the best doctor.");
            }
        } catch (RuntimeException e) {
            log.warn("Chat department lookup failed: {}", e.getMessage());
            response.setReply("I couldn't reach the department catalog right now — please try again shortly.");
        }
        return response;
    }

    private ChatResponseDto doctorLookupReply(String symptoms) {
        ChatResponseDto response = new ChatResponseDto();
        response.setIntent("FIND_DOCTOR");
        if (symptoms.isBlank()) {
            response.setReply("Tell me your symptoms (for example: \"Which doctor for persistent headache?\") " +
                    "and I'll suggest the best available specialist.");
            return response;
        }
        try {
            DoctorSuggestionResponseDto ranked = recommendationService.recommend(symptoms);
            response.setSuggestedDepartment(ranked.getSuggestedDepartment());
            response.setEmergency(ranked.isEmergency());
            if (ranked.isEmergency()) {
                response.setReply("⚠️ Your symptoms sound urgent — please seek immediate attention. " +
                        "Nearest available specialists:");
            } else if (ranked.getSuggestions().isEmpty()) {
                response.setReply("No doctors are currently accepting new patients — please try again shortly.");
                return response;
            } else {
                response.setReply("Based on your symptoms, these specialists are available now" +
                        (ranked.getSuggestedDepartment() != null ? " (" + ranked.getSuggestedDepartment() + ")" : "") + ":");
            }
            List<DoctorSuggestionDto> suggestions = ranked.getSuggestions().stream()
                    .limit(SUGGESTION_LIMIT)
                    .collect(Collectors.toList());
            response.setSuggestions(suggestions);
            // Name them in the reply too (the UI renders them as chips).
            String names = suggestions.stream()
                    .map(s -> s.getName() + (s.getDepartmentName() != null ? " (" + s.getDepartmentName() + ")" : ""))
                    .collect(Collectors.joining(", "));
            if (!names.isBlank()) {
                response.setReply(response.getReply() + " " + names + ".");
            }
        } catch (RuntimeException e) {
            log.warn("Chat doctor lookup failed: {}", e.getMessage());
            response.setReply("I couldn't reach the doctor catalog right now — please try again shortly.");
        }
        return response;
    }

    private ChatResponseDto fallbackReply() {
        ChatResponseDto response = new ChatResponseDto();
        response.setIntent("FALLBACK");
        response.setReply("I'm not sure I understood that. I can check your queue status, suggest a doctor " +
                "for symptoms, or list our departments. Try \"What's my queue status?\" or \"Which doctor for a fever?\"");
        return response;
    }
}
