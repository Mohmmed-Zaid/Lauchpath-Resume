package com.launchpath.resume_service.controller;

import com.launchpath.resume_service.dto.request.CreateResumeRequestDTO;
import com.launchpath.resume_service.dto.response.ApiResponseDTO;
import com.launchpath.resume_service.dto.response.ParsedResumeResponseDTO;
import com.launchpath.resume_service.dto.response.ResumeDetailResponseDTO;
import com.launchpath.resume_service.entity.ParsedResume;
import com.launchpath.resume_service.entity.Resume;
import com.launchpath.resume_service.entity.ResumeSection;
import com.launchpath.resume_service.enums.SectionType;
import com.launchpath.resume_service.exception.RateLimitExceededException;
import com.launchpath.resume_service.mapper.ResumeMapper;
import com.launchpath.resume_service.services.FileUploadService;
import com.launchpath.resume_service.services.ParserService;
import com.launchpath.resume_service.services.RateLimitService;
import com.launchpath.resume_service.services.ResumeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/upload")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileUploadService fileUploadService;
    private final ParserService parserService;
    private final ResumeService resumeService;
    private final ResumeMapper resumeMapper;
    private final RateLimitService rateLimitService;

    // ══════════════════════════════════════════════════════════
    // UPLOAD + PARSE
    // POST /api/v1/upload/resume
    // ══════════════════════════════════════════════════════════

    @PostMapping(value = "/resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponseDTO<ParsedResumeResponseDTO>> uploadResume(
            @RequestParam("file") MultipartFile file) {

        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException("Too many requests. Please wait 1 minute.");
        }
        log.info("Upload resume - userId: {}, filename: {}", userId, file.getOriginalFilename());

        Map<String, Object> uploadResult = fileUploadService.uploadResumeFile(file, userId);
        String cloudinaryId = (String) uploadResult.get("public_id");

        ParsedResume parsed = parserService.parseUploadedFile(file, cloudinaryId, userId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponseDTO.success(
                        "Resume uploaded and parsed successfully",
                        resumeMapper.toParsedResumeDTO(parsed)
                ));
    }

    // ══════════════════════════════════════════════════════════
    // GET PARSED RESULT
    // GET /api/v1/upload/resume/parsed
    // ══════════════════════════════════════════════════════════

    @GetMapping("/resume/parsed")
    public ResponseEntity<ApiResponseDTO<ParsedResumeResponseDTO>> getParsed() {
        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException("Too many requests. Please wait 1 minute.");
        }

        ParsedResume parsed = parserService.getParsedResume(userId);

        return ResponseEntity.ok(
                ApiResponseDTO.success("Parsed resume fetched",
                        resumeMapper.toParsedResumeDTO(parsed))
        );
    }

    // ══════════════════════════════════════════════════════════
    // CONFIRM PARSED — CREATE RESUME
    // POST /api/v1/upload/resume/confirm
    // FIX: converts parsedSections map → real ResumeSection list
    // ══════════════════════════════════════════════════════════

    @PostMapping("/resume/confirm")
    public ResponseEntity<ApiResponseDTO<ResumeDetailResponseDTO>> confirmParsed(
            @RequestBody CreateResumeRequestDTO request) {

        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException("Too many requests. Please wait 1 minute.");
        }
        log.info("Confirm parsed resume - userId: {}", userId);

        ParsedResume parsed = parserService.getParsedResume(userId);

        // ✅ FIX: Convert parsedSections map → List<ResumeSection>
        List<ResumeSection> sections = convertParsedSectionsToResumeSections(
                parsed.getParsedSections()
        );

        Resume resume = resumeService.createResumeFromParsed(
                userId,
                request.getTitle(),
                request.getTemplateId(),
                sections          // ← real sections, not empty list
        );

        parserService.deleteParsedResume(userId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponseDTO.success(
                        "Resume created from uploaded file",
                        resumeMapper.toDetailDTO(resume, userId)
                ));
    }

    // ══════════════════════════════════════════════════════════
    // DISCARD PARSED
    // DELETE /api/v1/upload/resume/parsed
    // ══════════════════════════════════════════════════════════

    @DeleteMapping("/resume/parsed")
    public ResponseEntity<ApiResponseDTO<Void>> discardParsed() {
        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException("Too many requests. Please wait 1 minute.");
        }
        parserService.deleteParsedResume(userId);
        return ResponseEntity.ok(ApiResponseDTO.success("Upload discarded"));
    }

    // ══════════════════════════════════════════════════════════
    // PRIVATE: Convert AI-parsed map → ResumeSection list
    // Maps the JSON structure from AiService.buildParsePrompt
    // to the ResumeSection entity structure the editor expects
    // ══════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private List<ResumeSection> convertParsedSectionsToResumeSections(
            Map<String, Object> parsedSections) {

        List<ResumeSection> sections = new ArrayList<>();

        if (parsedSections == null || parsedSections.isEmpty()) {
            // Return default empty sections so editor is still usable
            return buildDefaultEmptySections();
        }

        int order = 0;

        // ── Personal Info ─────────────────────────────────────
        Object personalInfo = parsedSections.get("personalInfo");
        if (personalInfo instanceof Map) {
            sections.add(ResumeSection.builder()
                    .sectionId(UUID.randomUUID().toString())
                    .type(SectionType.PERSONAL_INFO)
                    .title("Personal Information")
                    .order(order++)
                    .isVisible(true)
                    .content((Map<String, Object>) personalInfo)
                    .build());
        }

        // ── Summary ───────────────────────────────────────────
        Object summary = parsedSections.get("summary");
        if (summary != null) {
            Map<String, Object> summaryContent = new HashMap<>();
            summaryContent.put("text", summary.toString());
            sections.add(ResumeSection.builder()
                    .sectionId(UUID.randomUUID().toString())
                    .type(SectionType.SUMMARY)
                    .title("Professional Summary")
                    .order(order++)
                    .isVisible(true)
                    .content(summaryContent)
                    .build());
        }

        // ── Experience ────────────────────────────────────────
        // AI returns: [{company, role, startDate, endDate, description, bullets[]}]
        // Editor expects: {entries: [{company, role, startDate, endDate, bullets[]}]}
        Object experience = parsedSections.get("experience");
        if (experience instanceof List) {
            List<Map<String, Object>> expList = (List<Map<String, Object>>) experience;
            // Normalise: map "description" → add to bullets if bullets is empty
            List<Map<String, Object>> normalisedExp = new ArrayList<>();
            for (Map<String, Object> exp : expList) {
                Map<String, Object> e = new HashMap<>(exp);
                if (e.get("bullets") == null || ((List<?>) e.getOrDefault("bullets", List.of())).isEmpty()) {
                    Object desc = e.get("description");
                    if (desc != null && !desc.toString().isBlank()) {
                        e.put("bullets", List.of(desc.toString()));
                    } else {
                        e.put("bullets", new ArrayList<>());
                    }
                }
                normalisedExp.add(e);
            }
            Map<String, Object> expContent = new HashMap<>();
            expContent.put("entries", normalisedExp);
            sections.add(ResumeSection.builder()
                    .sectionId(UUID.randomUUID().toString())
                    .type(SectionType.EXPERIENCE)
                    .title("Work Experience")
                    .order(order++)
                    .isVisible(true)
                    .content(expContent)
                    .build());
        }

        // ── Education ─────────────────────────────────────────
        // AI returns: [{institution, degree, field, startDate, endDate, gpa}]
        // Editor expects: {entries: [...same...]}
        Object education = parsedSections.get("education");
        if (education instanceof List) {
            Map<String, Object> eduContent = new HashMap<>();
            eduContent.put("entries", education);
            sections.add(ResumeSection.builder()
                    .sectionId(UUID.randomUUID().toString())
                    .type(SectionType.EDUCATION)
                    .title("Education")
                    .order(order++)
                    .isVisible(true)
                    .content(eduContent)
                    .build());
        }

        // ── Skills ────────────────────────────────────────────
        // AI returns: [{category, skills[]}]  OR  ["skill1","skill2"]
        // Editor expects: {skills: ["skill1","skill2"]}
        Object skills = parsedSections.get("skills");
        if (skills != null) {
            Map<String, Object> skillsContent = new HashMap<>();
            if (skills instanceof List) {
                List<?> skillList = (List<?>) skills;
                if (!skillList.isEmpty() && skillList.get(0) instanceof Map) {
                    // [{category, skills[]}] → flatten all skills into one list
                    List<String> flat = new ArrayList<>();
                    for (Object item : skillList) {
                        Map<String, Object> categoryMap = (Map<String, Object>) item;
                        Object innerSkills = categoryMap.get("skills");
                        if (innerSkills instanceof List) {
                            ((List<?>) innerSkills).forEach(s -> flat.add(s.toString()));
                        }
                    }
                    skillsContent.put("skills", flat);
                } else {
                    // Already flat list
                    List<String> flat = new ArrayList<>();
                    skillList.forEach(s -> flat.add(s.toString()));
                    skillsContent.put("skills", flat);
                }
            }
            sections.add(ResumeSection.builder()
                    .sectionId(UUID.randomUUID().toString())
                    .type(SectionType.SKILLS)
                    .title("Skills")
                    .order(order++)
                    .isVisible(true)
                    .content(skillsContent)
                    .build());
        }

        // ── Projects ──────────────────────────────────────────
        // AI returns: [{name, description, techStack[], url}]
        // Editor expects: {entries: [{name, description, techStack[]}]}
        Object projects = parsedSections.get("projects");
        if (projects instanceof List) {
            Map<String, Object> projContent = new HashMap<>();
            projContent.put("entries", projects);
            sections.add(ResumeSection.builder()
                    .sectionId(UUID.randomUUID().toString())
                    .type(SectionType.PROJECTS)
                    .title("Projects")
                    .order(order)
                    .isVisible(true)
                    .content(projContent)
                    .build());
        }

        // If we ended up with no sections (AI returned garbage), fall back
        if (sections.isEmpty()) {
            return buildDefaultEmptySections();
        }

        return sections;
    }

    /**
     * Fallback: when AI parsing fails or returns nothing,
     * create 6 empty-content sections so the editor opens
     * and the user can type manually — same as "Create from scratch"
     */
    private List<ResumeSection> buildDefaultEmptySections() {
        List<ResumeSection> sections = new ArrayList<>();
        String[][] defaults = {
                {"PERSONAL_INFO", "Personal Information"},
                {"SUMMARY",       "Professional Summary"},
                {"EXPERIENCE",    "Work Experience"},
                {"EDUCATION",     "Education"},
                {"SKILLS",        "Skills"},
                {"PROJECTS",      "Projects"},
        };
        for (int i = 0; i < defaults.length; i++) {
            sections.add(ResumeSection.builder()
                    .sectionId(UUID.randomUUID().toString())
                    .type(SectionType.valueOf(defaults[i][0]))
                    .title(defaults[i][1])
                    .order(i)
                    .isVisible(true)
                    .content(new HashMap<>())
                    .build());
        }
        return sections;
    }

    private Long getCurrentUserId() {
        return (Long) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
    }
}