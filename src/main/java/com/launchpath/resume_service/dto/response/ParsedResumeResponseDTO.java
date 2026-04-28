package com.launchpath.resume_service.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class ParsedResumeResponseDTO {

    private String id;
    private String originalFileName;
    private Boolean isParsed;
    private String parseError;

    // Structured sections — user reviews before confirming
    private Map<String, Object> parsedSections;

    // Raw text — shown if AI parsing failed
    private String rawText;
}
