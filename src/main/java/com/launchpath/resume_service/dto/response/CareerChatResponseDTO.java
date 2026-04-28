package com.launchpath.resume_service.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class CareerChatResponseDTO {

    private String reply;
    private String resumeId;

    // Updated conversation history — frontend stores this
    // and sends back on next message for context
    private List<Map<String, String>> updatedHistory;
}