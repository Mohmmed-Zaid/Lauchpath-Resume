package com.launchpath.resume_service.dto.response;

import com.launchpath.resume_service.enums.AiProvider;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class AtsResultResponseDTO {

    private String resultId;
    private AiProvider provider;
    private LocalDateTime analyzedAt;
    private String jobDescription;

    // Core score
    private Integer overallScore;

    // 6 metric breakdown
    private Map<String, Integer> metricScores;

    // AI suggestions
    private List<String> suggestions;

    // Keyword analysis
    private List<String> matchedKeywords;
    private List<String> missingKeywords;

    // Score label for UI — EXCELLENT / GOOD / FAIR / POOR
    private String scoreLabel;
}
