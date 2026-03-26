package com.launchpath.resume_service.entity;

import com.launchpath.resume_service.entity.base.BaseDocument;
import com.launchpath.resume_service.enums.TemplateCategory;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Map;

@Document(collection = "templates")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Template extends BaseDocument {

    @Id
    private String id;

    private String name; // "Modern Professional", "Clean Minimal"

    private String description;

    private TemplateCategory category;

    // Free = accessible to all, Premium = paid plans only
    private Boolean isPremium;

    // Cloudinary URL for template thumbnail
    private String previewUrl;

    // HTML/CSS template string
    // Placeholders like {{firstName}}, {{experience}} replaced at export
    private String htmlTemplate;

    // Which sections this template supports and their default order
    // [{"type": "EXPERIENCE", "order": 1}, {"type": "EDUCATION", "order": 2}]
    private List<Map<String, Object>> sectionStructure;

    // Color scheme
    private String primaryColor;
    private String secondaryColor;
    private String fontFamily;

    // How many users have used this template — for popularity sorting
    private Integer usageCount;

    private Boolean isActive;
}
