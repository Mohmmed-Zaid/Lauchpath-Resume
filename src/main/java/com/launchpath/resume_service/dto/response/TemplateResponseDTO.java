package com.launchpath.resume_service.dto.response;

import com.launchpath.resume_service.enums.TemplateCategory;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class TemplateResponseDTO {

    private String id;
    private String name;
    private String description;
    private TemplateCategory category;
    private Boolean isPremium;
    private String previewUrl;
    private String primaryColor;
    private String secondaryColor;
    private String fontFamily;
    private Integer usageCount;
    private List<Map<String, Object>> sectionStructure;
}
