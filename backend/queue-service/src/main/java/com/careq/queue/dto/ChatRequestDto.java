package com.careq.queue.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "One message to the CareQ AI assistant.")
public class ChatRequestDto {

    @Schema(description = "Free-text message from the patient", example = "Which doctor should I see for a persistent headache?")
    @NotBlank(message = "message is required")
    @Size(max = 500, message = "message must be at most 500 characters")
    private String message;

    public ChatRequestDto() {
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
