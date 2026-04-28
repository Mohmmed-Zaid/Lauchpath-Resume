package com.launchpath.resume_service.feign.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class AiAnalyzeRequestDTO {

    private String userId;
    private String targetRole;
    private String experienceLevel;
    private List<String> skills;
    private String resumeHash;
    private ResumeDataDTO resumeData;

    @Getter
    @Setter
    @Builder
    public static class ResumeDataDTO {
        private List<String> projects;
        private List<String> experience;
        private List<String> education;
    }
}
