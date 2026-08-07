package com.careq.queue.client;

import com.careq.queue.dto.AiAssessment;
import com.careq.queue.entity.TriageLevel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Calls the Groq API (OpenAI-compatible chat completions) to assess symptoms.
 *
 * Endpoint : https://api.groq.com/openai/v1/chat/completions
 * Model    : llama-3.1-8b-instant (fast enough to feel near-instant to the patient)
 * Auth     : Bearer token read strictly from the GROQ_API_KEY environment variable
 *            (surfaced via ai.groq.api-key in application.yml — never hardcoded).
 * Timeout  : connect + read timeout from ai.groq.timeout-seconds (default 5s).
 *
 * The model is asked to reply with strict JSON:
 * {"triage": "<LEVEL>", "department": "<DEPARTMENT or empty>", "reason": "..."}
 * If the content is not parseable, empty is returned. The department must be
 * chosen from the caller-provided list of real departments (used by the AI
 * doctor-recommendation flow); when that list is empty the model is not asked
 * to pick a department and returns an empty string.
 */
@Component
public class GroqTriageAiClient implements TriageAiClient {

    private static final Logger log = LoggerFactory.getLogger(GroqTriageAiClient.class);

    /**
     * Exact prompt sent to Groq. The model must reply with strict JSON
     * containing a "triage" field set to one of EMERGENCY | HIGH | NORMAL | FOLLOW_UP
     * and a "department" field picked from the caller-provided list (empty when
     * the list is empty or nothing fits).
     */
    /**
     * Used when NO department list is provided (plain join-queue triage).
     * The department field is still returned (empty) so the JSON shape is
     * identical regardless of mode.
     */
    private static final String SYSTEM_PROMPT_TRIAGE_ONLY = """
            You are an AI triage assistant for a hospital outpatient queue system.
            Classify the patient's reported symptoms into EXACTLY ONE of these four levels:
            - EMERGENCY: life-threatening or severe, needs immediate attention
            - HIGH: serious condition, should be seen very soon
            - NORMAL: routine condition, standard queue order
            - FOLLOW_UP: mild or recurring, can wait or be handled by a follow-up visit
            Respond ONLY with a JSON object, no markdown, in exactly this shape:
            {"triage": "<LEVEL>", "department": "", "reason": "<one short sentence>"}
            The triage field must be exactly one of: EMERGENCY, HIGH, NORMAL, FOLLOW_UP.
            The department field must always be an empty string.""";

    /**
     * Used when a department list IS provided (AI doctor-recommendation flow):
     * the model additionally picks the single most likely department from the
     * caller-provided list of real departments.
     */
    private static final String SYSTEM_PROMPT_TEMPLATE = """
            You are an AI triage assistant for a hospital outpatient queue system.
            Classify the patient's reported symptoms into EXACTLY ONE of these four levels:
            - EMERGENCY: life-threatening or severe, needs immediate attention
            - HIGH: serious condition, should be seen very soon
            - NORMAL: routine condition, standard queue order
            - FOLLOW_UP: mild or recurring, can wait or be handled by a follow-up visit
            Also choose the SINGLE most likely department for this patient, using ONLY one
            of the listed department names. If none of the listed departments fits, return
            an empty string for department.
            Respond ONLY with a JSON object, no markdown, in exactly this shape:
            {"triage": "<LEVEL>", "department": "<DEPARTMENT or empty>", "reason": "<one short sentence>"}
            The triage field must be exactly one of: EMERGENCY, HIGH, NORMAL, FOLLOW_UP.
            Departments available: %s""";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final String model;
    private final String apiKey;
    private final boolean apiKeyPresent;

    public GroqTriageAiClient(
            @Value("${ai.groq.api-key}") String apiKey,
            @Value("${ai.groq.url}") String url,
            @Value("${ai.groq.model}") String model,
            @Value("${ai.groq.timeout-seconds:5}") int timeoutSeconds) {

        this.model = model;
        this.apiKey = apiKey;
        this.apiKeyPresent = apiKey != null && !apiKey.isBlank();

        // JdkClientHttpRequestFactory with explicit connect/read timeouts so a
        // slow or hanging LLM never blocks a patient's join for long.
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                        .build());
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        this.restClient = RestClient.builder()
                .baseUrl(url)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        if (!apiKeyPresent) {
            log.warn("GROQ_API_KEY is not configured — AI triage will always fall back to NORMAL");
        }
    }

    @Override
    public Optional<AiAssessment> assess(String symptomText, List<String> allowedDepartments) throws Exception {
        if (!apiKeyPresent) {
            throw new IllegalStateException("GROQ_API_KEY is not configured");
        }

        boolean hasDepartments = allowedDepartments != null && !allowedDepartments.isEmpty();
        String systemPrompt = hasDepartments
                ? String.format(SYSTEM_PROMPT_TEMPLATE, String.join(", ", allowedDepartments))
                : SYSTEM_PROMPT_TRIAGE_ONLY;

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "temperature", 0.0,
                "max_tokens", 256,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", symptomText)
                )
        );

        log.info("Calling Groq symptom assessment (model={})", model);

        Map<?, ?> response = restClient.post()
                .uri("")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        return parseAssessment(response);
    }

    /**
     * Groq returns {"choices":[{"message":{"content":"{\"triage\":\"HIGH\",\"department\":\"Cardiology\",...}"}}]}.
     * The content field is itself a JSON string which we parse into an AiAssessment.
     */
    private Optional<AiAssessment> parseAssessment(Map<?, ?> response) {
        if (response == null) {
            return Optional.empty();
        }
        String content = "";
        try {
            JsonNode root = OBJECT_MAPPER.valueToTree(response);
            content = root.path("choices").path(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                return Optional.empty();
            }
            JsonNode parsed = OBJECT_MAPPER.readTree(content);
            String level = parsed.path("triage").asText("").trim().toUpperCase();
            String department = parsed.path("department").asText("").trim();
            try {
                return Optional.of(new AiAssessment(
                        TriageLevel.valueOf(level),
                        department.isEmpty() ? null : department));
            } catch (IllegalArgumentException e) {
                log.warn("Groq returned an unrecognized triage level '{}' — treating as unparseable", level);
                return Optional.empty();
            }
        } catch (Exception e) {
            String preview = content.length() > 200
                    ? content.substring(0, 200) + "…"
                    : content;
            log.warn("Could not parse Groq assessment response (content='{}'): {}", preview, e.getMessage());
            return Optional.empty();
        }
    }
}
