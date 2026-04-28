package com.launchpath.resume_service.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CareerGuidanceResponseDTO {

    private String resumeId;
    private String targetRole;
    private String currentLevel;      // FRESHER / JUNIOR / MID / SENIOR

    // Skill gaps with salary impact
    private List<SkillGapDTO> skillGaps;

    // Career progression paths
    private List<String> nextCareerMoves;

    // Companies actively hiring
    private List<CompanySuggestionDTO> companySuggestions;

    // Interview prep
    private List<String> interviewQuestions;

    // Estimated salary range for current profile
    private String currentSalaryRange;

    // Estimated salary after filling skill gaps
    private String potentialSalaryRange;

    // Overall career advice
    private String careerAdvice;

    @Getter
    @Builder
    public static class SkillGapDTO {
        private String skill;
        private String importance;       // HIGH / MEDIUM / LOW
        private String salaryImpact;     // "30-40% hike" hwo o ge he hike
        private List<String> resources;  // learning links
        private String timeToLearn;      // "4-6 weeks"
    }

    @Getter
    @Builder
    public static class CompanySuggestionDTO {
        private String name;
        private String type;             // product/service/startup
        private String location;
        private String salaryRange;
        private String whyGoodFit;
        private String applyUrl;
    }
}

