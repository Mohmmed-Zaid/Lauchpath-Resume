package com.launchpath.resume_service.dto.response;


import com.launchpath.resume_service.entity.AtsResult;
import com.launchpath.resume_service.entity.ResumeSection;
import com.launchpath.resume_service.enums.ResumeStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ResumeDetailResponseDTO {

    private String id;
    private String userId;
    private String title;
    private ResumeStatus status;
    private String templateId;
    private String targetJobTitle;
    private String targetCompany;
    private Integer currentVersion;
    private Boolean isLocked;
    private Boolean isLockedByMe;    // true if locked by requesting user

    // Full sections — editor needs all content
    private List<ResumeSection> sections;

    // Version list — lightweight, no section content in list
    private List<VersionSummaryDTO> versionHistory;

    // Latest ATS result only — full history via separate endpoint
    private AtsResult latestAtsResult;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Nested version summary — avoids sending full section content
    // in version list
    @Getter
    @Builder
    public static class VersionSummaryDTO {
        private Integer versionNumber;
        private String savedBy;
        private String label;
        private LocalDateTime savedAt;
    }
}