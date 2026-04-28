package com.launchpath.resume_service.dto.response;

import com.launchpath.resume_service.enums.SectionType;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class ResumeSectionResponseDTO {

    private String sectionId;
    private SectionType type;
    private String title;
    private Integer order;
    private Boolean isVisible;
    private Map<String, Object> content;
}
