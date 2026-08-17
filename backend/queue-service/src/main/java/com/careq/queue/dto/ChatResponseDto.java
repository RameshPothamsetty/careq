package com.careq.queue.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Reply from the CareQ AI assistant.")
public class ChatResponseDto {

    @Schema(description = "Intent the assistant detected", example = "FIND_DOCTOR")
    private String intent;

    @Schema(description = "Human-readable reply shown in the chat", example = "Based on your symptoms, these specialists are available now:")
    private String reply;

    @Schema(description = "Ranked doctor suggestions (doctor-lookup intents only)")
    private List<DoctorSuggestionDto> suggestions;

    @Schema(description = "Suggested department for symptom intents", example = "Neurology")
    private String suggestedDepartment;

    @Schema(description = "True when the assistant flagged the symptoms as urgent", example = "false")
    private boolean emergency;

    public ChatResponseDto() {
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public List<DoctorSuggestionDto> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<DoctorSuggestionDto> suggestions) {
        this.suggestions = suggestions;
    }

    public String getSuggestedDepartment() {
        return suggestedDepartment;
    }

    public void setSuggestedDepartment(String suggestedDepartment) {
        this.suggestedDepartment = suggestedDepartment;
    }

    public boolean isEmergency() {
        return emergency;
    }

    public void setEmergency(boolean emergency) {
        this.emergency = emergency;
    }
}
