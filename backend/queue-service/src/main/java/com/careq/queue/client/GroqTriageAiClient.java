package com.careq.queue.client;

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
 * Calls the Groq API (OpenAI-compatible chat completions) to triage symptoms.
 *
 * Endpoint : https://api.groq.com/openai/v1/chat/completions
 * Model    : llama-3.1-8b-instant (fast enough to feel near-instant to the patient)
 * Auth     : Bearer token read strictly from the GROQ_API_KEY environment variable
 *            (surfaced via ai.groq.api-key in application.yml — never hardcoded).
 * Timeout  : connect + read timeout from ai.groq.timeout-seconds (default 5s).
 *
 * The model is asked to reply with strict JSON: {"triage": "<LEVEL>", "reason": "..."}.
 * If the content is not parseable into a TriageLevel, empty is returned.
 */
@Component
public class GroqTriageAiClient implements TriageAiClient {

    private static final Logger log = LoggerFactory.getLogger(GroqTriageAiClient.class);

    /**
     * Exact prompt sent to Groq. The model must reply with strict JSON
     * containing a "triage" field set to one of EMERGENCY | HIGH | NORMAL | FOLLOW_UP.
     */
    private static final String SYSTEM_PROMPT = """
            You are an AI triage assistant for a hospital outpatient queue system.
            Classify the patient's reported symptoms into EXACTLY ONE of these four levels:
            - EMERGENCY: life-threatening or severe, needs immediate attention
            - HIGH: serious condition, should be seen very soon
            - NORMAL: routine condition, standard queue order
            - FOLLOW_UP: mild or recurring, can wait or be handled by a follow-up visit
            Respond ONLY with a JSON object, no markdown, in exactly this shape:
            {"triage": "<LEVEL>", "reason": "<one short sentence>"}
            The triage field must be exactly one of: EMERGENCY, HIGH, NORMAL, FOLLOW_UP.""";

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
    public Optional<TriageLevel> classify(String symptomText) throws Exception {
        if (!apiKeyPresent) {
            throw new IllegalStateException("GROQ_API_KEY is not configured");
        }

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "temperature", 0.0,
                "max_tokens", 64,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", symptomText)
                )
        );

        log.info("Calling Groq symptom triage (model={})", model);

        Map<?, ?> response = restClient.post()
                .uri("")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        return parseTriage(response);
    }

    /**
     * Groq returns {"choices":[{"message":{"content":"{\\"triage\\":\\"HIGH\\",...}"}}]}.
     * The content field is itself a JSON string which we parse into a TriageLevel.
     */
    private Optional<TriageLevel> parseTriage(Map<?, ?> response) {
        if (response == null) {
            return Optional.empty();
        }
        try {
            JsonNode root = OBJECT_MAPPER.valueToTree(response);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                return Optional.empty();
            }
            JsonNode parsed = OBJECT_MAPPER.readTree(content);
            String level = parsed.path("triage").asText("").trim().toUpperCase();
            try {
                return Optional.of(TriageLevel.valueOf(level));
            } catch (IllegalArgumentException e) {
                log.warn("Groq returned an unrecognized triage level '{}' — treating as unparseable", level);
                return Optional.empty();
            }
        } catch (Exception e) {
            log.warn("Could not parse Groq triage response: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
