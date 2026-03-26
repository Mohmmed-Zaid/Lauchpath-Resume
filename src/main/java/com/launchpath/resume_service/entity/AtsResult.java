package com.launchpath.resume_service.entity;

import com.launchpath.resume_service.enums.AiProvider;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AtsResult {

    private String resultId;

    private AiProvider provider;

    private LocalDateTime analyzedAt;

    // Job description user pasted (optional)
    private String jobDescription;

    // Overall score out of 100
    private Integer overallScore;

    // Breakdown by 6 metrics
    // key = metric name, value = score
    // "keywords" → 16, "actionVerbs" → 12 etc
    private Map<String, Integer> metricScores;

    // AI-generated suggestions list
    private List<String> suggestions;

    // Keywords found in resume
    private List<String> matchedKeywords;

    // Keywords missing (from job description if provided)
    private List<String> missingKeywords;

    // Raw AI response stored for debugging
    private String rawAiResponse;
}
