package com.launchpath.resume_service.dto.request;

import lombok.*;

import java.util.List;

/**
 * DTO for the AI Resume Generator wizard.
 * Fields match exactly what ResumeGeneratorPage.tsx sends.
 *
 * Frontend sends:
 *   fullName, email, phone, location, targetRole,
 *   experienceLevel, experience (plain text), isFresher,
 *   degree, institution, cgpa, graduationYear,
 *   skills (string[]), projects (plain text), templateId
 *
 * This DTO also supports the structured format used by
 * ResumeEnhancementService.buildGeneratePrompt (experiences/educations lists).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateResumeRequestDTO {

    // ── Personal info ──────────────────────────────────────
    private String fullName;
    private String email;
    private String phone;
    private String location;

    // ── Target ────────────────────────────────────────────
    private String targetRole;         // "Software Engineer"
    private String experienceLevel;    // "FRESHER" | "JUNIOR" | "MID" | "SENIOR"
    private String templateId;         // "professional"

    // ── Experience ────────────────────────────────────────
    // Frontend wizard sends raw textarea text as `experience` (string).
    // We wrap it in a list for the AI prompt builder.
    private String experience;         // raw text from wizard textarea
    private Boolean isFresher;

    // Structured form (used when calling from other services)
    private List<String> experiences;  // list form — optional

    // ── Education ─────────────────────────────────────────
    // Frontend wizard sends flat fields:
    private String degree;
    private String institution;
    private String cgpa;
    private String graduationYear;

    // Structured form — optional
    private List<Object> educations;

    // ── Skills & Projects ─────────────────────────────────
    private List<String> skills;       // ["Java", "Spring Boot"]
    private String projects;           // raw textarea text from wizard
    private List<Object> projectsList; // structured — optional

    // ── Optional summary ──────────────────────────────────
    private String summary;            // pre-written summary if any

    // ──────────────────────────────────────────────────────
    // Convenience getters used by buildGeneratePrompt
    // These normalise flat wizard fields → list form that
    // the AI prompt builder (ResumeEnhancementService) expects
    // ──────────────────────────────────────────────────────

    /**
     * Returns experience as list.
     * If structured `experiences` list provided → use that.
     * Else wrap the raw `experience` textarea text.
     */
    public List<String> getExperiences() {
        if (experiences != null && !experiences.isEmpty()) return experiences;
        if (experience != null && !experience.isBlank()) return List.of(experience);
        return List.of();
    }

    /**
     * Returns education as list.
     * If structured `educations` list provided → use that.
     * Else build a readable string from wizard flat fields.
     */
    public List<Object> getEducations() {
        if (educations != null && !educations.isEmpty()) return educations;
        if (institution != null && !institution.isBlank()) {
            String edu = degree + " — " + institution
                    + (cgpa != null && !cgpa.isBlank() ? ", CGPA: " + cgpa : "")
                    + (graduationYear != null && !graduationYear.isBlank()
                    ? " (" + graduationYear + ")" : "");
            return List.of(edu);
        }
        return List.of();
    }

    /**
     * Returns projects as list.
     * Wraps raw textarea text if no structured list.
     */
    public List<Object> getProjects() {
        if (projectsList != null && !projectsList.isEmpty()) return projectsList;
        if (projects != null && !projects.isBlank()) return List.of(projects);
        return List.of();
    }
}