package com.launchpath.resume_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class CareerChatRequestDTO {

    @NotBlank(message = "Message is required")
    private String message;

    // Previous conversation history for context
    // [{"role": "user", "content": "..."}, {"role": "assistant", "content": "..."}]
    private List<Map<String, String>> conversationHistory;
}