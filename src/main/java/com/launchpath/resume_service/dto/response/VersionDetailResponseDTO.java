package com.launchpath.resume_service.dto.response;


import com.launchpath.resume_service.entity.ResumeSection;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class VersionDetailResponseDTO {

    private Integer versionNumber;
    private String savedBy;
    private String label;
    private LocalDateTime savedAt;
    private List<ResumeSection> sections; // full sections for preview
}
