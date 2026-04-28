package com.launchpath.resume_service.dto.response;



import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class OptimizeResumeResponseDTO {

    private String resumeId;
    private String targetJobTitle;

    // Improved sections — key = sectionId, value = improved content
    private List<ImprovedSectionDTO> improvedSections;

    // Overall improvement summary
    private String summary;

    // How many bullets were improved
    private Integer bulletsImproved;

    @Getter
    @Builder
    public static class ImprovedSectionDTO {
        private String sectionId;
        private String sectionTitle;
        private Map<String, Object> originalContent;
        private Map<String, Object> improvedContent;
        private List<String> changes; // what was changed and why
    }
}