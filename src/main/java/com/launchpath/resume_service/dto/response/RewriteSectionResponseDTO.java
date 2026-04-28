package com.launchpath.resume_service.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class RewriteSectionResponseDTO {

    private String resumeId;
    private String jobDescription;

    // Rewritten sections tailored to JD
    private List<RewrittenSectionDTO> rewrittenSections;

    // Keywords from JD that were added
    private List<String> keywordsAdded;

    // Match score before and after
    private Integer matchScoreBefore;
    private Integer matchScoreAfter;

    @Getter
    @Builder
    public static class RewrittenSectionDTO {
        private String sectionId;
        private String sectionTitle;
        private Map<String, Object> originalContent;
        private Map<String, Object> rewrittenContent;
    }
}
