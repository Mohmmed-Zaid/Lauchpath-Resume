package com.launchpath.resume_service.feign.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalyzeResponseDTO {

    private Integer readinessScore;
    private String readinessLabel;
    private List<String> strengths;
    private List<String> skillGaps;
    private List<String> recommendations;
    private Map<String, List<String>> learningLinks;
    private Boolean fromCache;
    private String errorMessage; // populated on fallback only
}
