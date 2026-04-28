package com.launchpath.resume_service.dto.response;

import com.launchpath.resume_service.enums.ResumeStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ResumeSummaryResponseDTO {

    private String id;
    private String title;
    private ResumeStatus status;
    private String templateId;
    private String targetJobTitle;
    private String targetCompany;
    private Integer currentVersion;
    private Integer sectionsCount;
    private Integer atsResultsCount;
    private Integer latestAtsScore;    // null if never analyzed
    private Boolean isLocked;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
