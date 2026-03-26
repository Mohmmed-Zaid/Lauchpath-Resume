package com.launchpath.resume_service.entity;

import com.launchpath.resume_service.enums.SectionType;
import lombok.*;

import java.util.Map;

// No @Document — this is embedded inside Resume
// Not stored in its own collection
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeSection {

    // Unique within a resume — UUID generated in service
    private String sectionId;

    private SectionType type;

    // Display order in the resume (0-based)
    private Integer order;

    // Section title — can be customized ("Work Experience" vs "Experience")
    private String title;

    // Visible or hidden in this resume
    private Boolean isVisible;

    // Flexible content — different structure per section type
    // EXPERIENCE: [{company, role, startDate, endDate, description, bullets[]}]
    // EDUCATION:  [{institution, degree, field, startDate, endDate, gpa}]
    // SKILLS:     [{category, skills[]}]
    // SUMMARY:    {text}
    // PROJECTS:   [{name, description, techStack[], url, bullets[]}]
    // Store as Map<String, Object> — MongoDB handles arbitrary JSON natively
    private Map<String, Object> content;
}
