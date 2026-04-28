package com.launchpath.resume_service.dto.request;

// Admin only
import com.launchpath.resume_service.enums.TemplateCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class CreateTemplateRequestDTO {

    @NotBlank(message = "Template name is required")
    private String name;

    private String description;

    @NotNull(message = "Category is required")
    private TemplateCategory category;

    @NotNull(message = "isPremium is required")
    private Boolean isPremium;

    private String primaryColor;
    private String secondaryColor;
    private String fontFamily;
    private String previewUrl;
    private List<Map<String, Object>> sectionStructure;
}
