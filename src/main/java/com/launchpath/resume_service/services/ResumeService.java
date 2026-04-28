package com.launchpath.resume_service.services;


import com.launchpath.resume_service.entity.Resume;
import com.launchpath.resume_service.entity.ResumeSection;
import com.launchpath.resume_service.entity.ResumeVersion;
import com.launchpath.resume_service.enums.ResumeStatus;
import com.launchpath.resume_service.enums.SectionType;
import com.launchpath.resume_service.exception.ResourceNotFoundException;
import com.launchpath.resume_service.exception.ResumeLimitExceededException;
import com.launchpath.resume_service.exception.UnauthorizedAccessException;
import com.launchpath.resume_service.feign.AiServiceClient;
import com.launchpath.resume_service.feign.UserServiceClient;
import com.launchpath.resume_service.feign.dto.AiAnalyzeRequestDTO;
import com.launchpath.resume_service.repo.ResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeService {

    private final ResumeRepository resumeRepository;
    private final UserServiceClient userServiceClient;
    private final HashingService hashingService;
    private final AiServiceClient aiServiceClient;
    // Max versions kept per resume — older ones trimmed
    private static final int MAX_VERSIONS = 50;

    // ══════════════════════════════════════════════════════════
    // CREATE
    // ══════════════════════════════════════════════════════════

    /**
     * Creates a blank resume with default sections.
     * Checks plan limit via Feign before creating.
     * Atomically creates resume + initial version snapshot.
     */
    public Resume createResume(Long userId, String title,
                               String templateId, String targetJobTitle) {
        log.info("Creating resume - userId: {}, title: {}", userId, title);

        // 1. Check resume creation limit via user-service
        long currentCount = resumeRepository
                .countByUserIdAndStatusNot(
                        String.valueOf(userId), ResumeStatus.ARCHIVED
                );

        Boolean canCreate = userServiceClient
                .canCreateMoreResumes(userId, currentCount)
                .getData();

        if (Boolean.FALSE.equals(canCreate)) {
            log.warn("Resume limit reached for userId: {}", userId);
            throw new ResumeLimitExceededException(
                    "Resume limit reached for your plan. " +
                            "Please upgrade or archive existing resumes."
            );
        }

        // 2. Build default sections for new resume
        List<ResumeSection> defaultSections = buildDefaultSections();

        // 3. Build resume
        Resume resume = Resume.builder()
                .userId(String.valueOf(userId))
                .title(title != null ? title.trim() : "My Resume")
                .status(ResumeStatus.DRAFT)
                .templateId(templateId)
                .targetJobTitle(targetJobTitle)
                .currentVersion(1)
                .sections(defaultSections)
                .versions(new ArrayList<>())
                .atsResults(new ArrayList<>())
                .exportedFileIds(new ArrayList<>())
                .isLocked(false)
                .build();

        // 4. Save initial version snapshot
        ResumeVersion initialVersion = ResumeVersion.builder()
                .versionNumber(1)
                .savedBy("USER")
                .savedAt(LocalDateTime.now())
                .label("Initial")
                .sections(deepCopySections(defaultSections))
                .build();

        resume.getVersions().add(initialVersion);

        Resume saved = resumeRepository.save(resume);
        log.info("Resume created - id: {}", saved.getId());
        return saved;
    }

    /**
     * Creates resume from parsed upload.
     * Called after user confirms parsed sections from uploaded PDF/DOCX.
     */
    public Resume createResumeFromParsed(Long userId,
                                         String title,
                                         String templateId,
                                         List<ResumeSection> parsedSections) {
        log.info("Creating resume from parsed - userId: {}", userId);

        long currentCount = resumeRepository
                .countByUserIdAndStatusNot(
                        String.valueOf(userId), ResumeStatus.ARCHIVED
                );

        Boolean canCreate = userServiceClient
                .canCreateMoreResumes(userId, currentCount)
                .getData();

        if (Boolean.FALSE.equals(canCreate)) {
            throw new ResumeLimitExceededException(
                    "Resume limit reached. Please upgrade or archive existing resumes."
            );
        }

        Resume resume = Resume.builder()
                .userId(String.valueOf(userId))
                .title(title != null ? title.trim() : "Imported Resume")
                .status(ResumeStatus.DRAFT)
                .templateId(templateId)
                .currentVersion(1)
                .sections(parsedSections)
                .versions(new ArrayList<>())
                .atsResults(new ArrayList<>())
                .exportedFileIds(new ArrayList<>())
                .isLocked(false)
                .build();

        ResumeVersion initialVersion = ResumeVersion.builder()
                .versionNumber(1)
                .savedBy("IMPORT")
                .savedAt(LocalDateTime.now())
                .label("Imported from upload")
                .sections(deepCopySections(parsedSections))
                .build();

        resume.getVersions().add(initialVersion);

        Resume saved = resumeRepository.save(resume);
        log.info("Resume created from parsed - id: {}", saved.getId());
        return saved;
    }

    // ══════════════════════════════════════════════════════════
    // READ
    // ══════════════════════════════════════════════════════════

    public Resume getResumeById(String resumeId, Long userId) {
        log.debug("Fetching resume - id: {}, userId: {}", resumeId, userId);
        return resumeRepository
                .findByIdAndUserId(resumeId, String.valueOf(userId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Resume not found: " + resumeId
                ));
    }


    @Transactional
    public Resume saveResume(String resumeId, Long userId,
                             List<ResumeSection> updatedSections,
                             String savedBy) {
        log.info("Saving resume - id: {}, userId: {}", resumeId, userId);

        Resume resume = getResumeById(resumeId, userId);
        resume.setSections(updatedSections);

        int newVersionNumber = resume.getCurrentVersion() + 1;
        resume.setCurrentVersion(newVersionNumber);

        // Generate hash from updated sections
        String newHash = hashingService.hashSections(updatedSections);

        ResumeVersion version = ResumeVersion.builder()
                .versionNumber(newVersionNumber)
                .savedBy(savedBy)
                .savedAt(LocalDateTime.now())
                .sections(deepCopySections(updatedSections))
                .build();

        resume.getVersions().add(version);

        if (resume.getVersions().size() > MAX_VERSIONS) {
            resume.getVersions().remove(0);
        }

        if (isResumeComplete(updatedSections)) {
            resume.setStatus(ResumeStatus.COMPLETE);
        }

        Resume saved = resumeRepository.save(resume);

        // Trigger ai-service analysis async after save
        // Only on USER save — not AUTO_SAVE (too frequent)
        if ("USER".equals(savedBy)) {
            triggerAiAnalysis(saved, newHash);
        }

        log.info("Resume saved - id: {}, version: {}", resumeId, newVersionNumber);
        return saved;
    }

    // ── Trigger AI analysis — non-blocking ───────────────────
    private void triggerAiAnalysis(Resume resume, String resumeHash) {
        try {
            log.info("Triggering AI analysis - resumeId: {}", resume.getId());

            // Extract section data for ai-service
            List<String> experience = extractSectionContent(
                    resume, "EXPERIENCE"
            );
            List<String> education = extractSectionContent(
                    resume, "EDUCATION"
            );
            List<String> projects = extractSectionContent(
                    resume, "PROJECTS"
            );
            List<String> skills = extractSkills(resume);

            AiAnalyzeRequestDTO aiRequest = AiAnalyzeRequestDTO.builder()
                    .userId(resume.getUserId())
                    .targetRole(resume.getTargetJobTitle() != null
                            ? resume.getTargetJobTitle()
                            : "Software Engineer")
                    .experienceLevel("JUNIOR") // Phase 3: from user profile
                    .skills(skills)
                    .resumeHash(resumeHash)
                    .resumeData(
                            AiAnalyzeRequestDTO.ResumeDataDTO.builder()
                                    .experience(experience)
                                    .education(education)
                                    .projects(projects)
                                    .build()
                    )
                    .build();

            // Feign call — fallback handles ai-service being down
            var response = aiServiceClient.analyzeCareer(aiRequest);

            if (response.isSuccess() && response.getData() != null) {
                log.info("AI analysis triggered - score: {}",
                        response.getData().getReadinessScore());
            } else {
                log.warn("AI analysis unavailable for resumeId: {}",
                        resume.getId());
            }

        } catch (Exception e) {
            // Never fail resume save because AI failed
            log.error("AI trigger failed for resumeId: {} — resume save " +
                    "still succeeded", resume.getId(), e);
        }
    }

    // ── Extract helpers ───────────────────────────────────────
    private List<String> extractSectionContent(Resume resume,
                                               String sectionType) {
        return resume.getSections().stream()
                .filter(s -> s.getType().name().equals(sectionType))
                .filter(s -> s.getContent() != null)
                .map(s -> s.getContent().toString())
                .toList();
    }

    private List<String> extractSkills(Resume resume) {
        return resume.getSections().stream()
                .filter(s -> s.getType().name().equals("SKILLS"))
                .filter(s -> s.getContent() != null)
                .flatMap(s -> {
                    Object skillsObj = s.getContent().get("skills");
                    if (skillsObj instanceof List<?> list) {
                        return list.stream().map(Object::toString);
                    }
                    return java.util.stream.Stream.empty();
                })
                .toList();
    }

    // Lightweight list — no sections/versions loaded
    // Uses projection query for performance
    public List<Resume> getResumeSummaries(Long userId) {
        log.debug("Fetching resume summaries for userId: {}", userId);
        return resumeRepository.findResumesSummaryByUserId(
                String.valueOf(userId)
        );
    }

    public List<Resume> getAllResumes(Long userId) {
        return resumeRepository.findByUserIdAndStatusNot(
                String.valueOf(userId), ResumeStatus.ARCHIVED
        );
    }

    public Page<Resume> getResumesPaginated(Long userId, Pageable pageable) {
        return resumeRepository.findByUserIdAndStatusNot(
                String.valueOf(userId), ResumeStatus.ARCHIVED, pageable
        );
    }

    public long getResumeCount(Long userId) {
        return resumeRepository.countByUserIdAndStatusNot(
                String.valueOf(userId), ResumeStatus.ARCHIVED
        );
    }

    /**
     * Auto-save — called by WebSocket on debounce.
     * Same as saveResume but labels version as AUTO_SAVE.
     * Does NOT increment version counter visibly.
     */
    public Resume autoSave(String resumeId, Long userId,
                           List<ResumeSection> sections) {
        log.debug("Auto-saving resume: {}", resumeId);
        return saveResume(resumeId, userId, sections, "AUTO_SAVE");
    }

    // ══════════════════════════════════════════════════════════
    // UPDATE — METADATA
    // ══════════════════════════════════════════════════════════

    public Resume updateResumeMetadata(String resumeId, Long userId,
                                       String title, String targetJobTitle,
                                       String targetCompany, String templateId) {
        log.info("Updating metadata - resumeId: {}", resumeId);

        Resume resume = getResumeById(resumeId, userId);

        if (title != null && !title.isBlank()) {
            resume.setTitle(title.trim());
        }
        if (targetJobTitle != null) {
            resume.setTargetJobTitle(targetJobTitle.trim());
        }
        if (targetCompany != null) {
            resume.setTargetCompany(targetCompany.trim());
        }
        if (templateId != null) {
            resume.setTemplateId(templateId);
        }

        return resumeRepository.save(resume);
    }

    // ══════════════════════════════════════════════════════════
    // UPDATE — SECTIONS CRUD
    // ══════════════════════════════════════════════════════════

    public Resume addSection(String resumeId, Long userId,
                             ResumeSection newSection) {
        log.info("Adding section - resumeId: {}, type: {}",
                resumeId, newSection.getType());

        Resume resume = getResumeById(resumeId, userId);

        // Generate unique section ID
        newSection.setSectionId(UUID.randomUUID().toString());

        // Set order to end of list
        newSection.setOrder(resume.getSections().size());
        newSection.setIsVisible(true);

        resume.getSections().add(newSection);
        return resumeRepository.save(resume);
    }

    public Resume updateSection(String resumeId, Long userId,
                                String sectionId,
                                Map<String, Object> updatedContent) {
        log.info("Updating section - resumeId: {}, sectionId: {}",
                resumeId, sectionId);

        Resume resume = getResumeById(resumeId, userId);

        resume.getSections().stream()
                .filter(s -> s.getSectionId().equals(sectionId))
                .findFirst()
                .ifPresentOrElse(
                        section -> section.setContent(updatedContent),
                        () -> { throw new ResourceNotFoundException(
                                "Section not found: " + sectionId); }
                );

        return resumeRepository.save(resume);
    }

    public Resume deleteSection(String resumeId, Long userId,
                                String sectionId) {
        log.info("Deleting section - resumeId: {}, sectionId: {}",
                resumeId, sectionId);

        Resume resume = getResumeById(resumeId, userId);

        boolean removed = resume.getSections()
                .removeIf(s -> s.getSectionId().equals(sectionId));

        if (!removed) {
            throw new ResourceNotFoundException(
                    "Section not found: " + sectionId
            );
        }

        // Reorder remaining sections
        reorderSections(resume.getSections());
        return resumeRepository.save(resume);
    }

    public Resume reorderSection(String resumeId, Long userId,
                                 String sectionId, int newOrder) {
        log.info("Reordering section - resumeId: {}, sectionId: {}, order: {}",
                resumeId, sectionId, newOrder);

        Resume resume = getResumeById(resumeId, userId);

        List<ResumeSection> sections = resume.getSections();

        // Find section and update order
        sections.stream()
                .filter(s -> s.getSectionId().equals(sectionId))
                .findFirst()
                .ifPresentOrElse(
                        s -> s.setOrder(newOrder),
                        () -> { throw new ResourceNotFoundException(
                                "Section not found: " + sectionId); }
                );

        // Sort by new order
        sections.sort(Comparator.comparingInt(ResumeSection::getOrder));
        resume.setSections(sections);
        return resumeRepository.save(resume);
    }

    public Resume toggleSectionVisibility(String resumeId, Long userId,
                                          String sectionId) {
        Resume resume = getResumeById(resumeId, userId);

        resume.getSections().stream()
                .filter(s -> s.getSectionId().equals(sectionId))
                .findFirst()
                .ifPresentOrElse(
                        s -> s.setIsVisible(!s.getIsVisible()),
                        () -> { throw new ResourceNotFoundException(
                                "Section not found: " + sectionId); }
                );

        return resumeRepository.save(resume);
    }

    // ══════════════════════════════════════════════════════════
    // VERSION HISTORY
    // ══════════════════════════════════════════════════════════

    public List<ResumeVersion> getVersionHistory(String resumeId, Long userId) {
        log.debug("Fetching version history - resumeId: {}", resumeId);
        Resume resume = getResumeById(resumeId, userId);
        return resume.getVersions();
    }

    /**
     * Restore resume to a previous version.
     * Current state saved as new version before restoring.
     * So user never loses current work.
     */
    public Resume restoreVersion(String resumeId, Long userId,
                                 int versionNumber) {
        log.info("Restoring version {} for resumeId: {}", versionNumber, resumeId);

        Resume resume = getResumeById(resumeId, userId);

        // Find requested version
        ResumeVersion targetVersion = resume.getVersions().stream()
                .filter(v -> v.getVersionNumber() == versionNumber)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Version not found: " + versionNumber
                ));

        // Save current state as new version before restoring
        int newVersionNumber = resume.getCurrentVersion() + 1;
        ResumeVersion currentSnapshot = ResumeVersion.builder()
                .versionNumber(newVersionNumber)
                .savedBy("BEFORE_RESTORE")
                .savedAt(LocalDateTime.now())
                .label("Saved before restore to v" + versionNumber)
                .sections(deepCopySections(resume.getSections()))
                .build();
        resume.getVersions().add(currentSnapshot);

        // Restore target version sections
        resume.setSections(deepCopySections(targetVersion.getSections()));
        resume.setCurrentVersion(newVersionNumber + 1);

        // Add restored version marker
        ResumeVersion restoredVersion = ResumeVersion.builder()
                .versionNumber(newVersionNumber + 1)
                .savedBy("RESTORE")
                .savedAt(LocalDateTime.now())
                .label("Restored from v" + versionNumber)
                .sections(deepCopySections(targetVersion.getSections()))
                .build();
        resume.getVersions().add(restoredVersion);

        Resume saved = resumeRepository.save(resume);
        log.info("Version {} restored for resumeId: {}", versionNumber, resumeId);
        return saved;
    }

    public Resume labelVersion(String resumeId, Long userId,
                               int versionNumber, String label) {
        Resume resume = getResumeById(resumeId, userId);

        resume.getVersions().stream()
                .filter(v -> v.getVersionNumber() == versionNumber)
                .findFirst()
                .ifPresentOrElse(
                        v -> v.setLabel(label),
                        () -> { throw new ResourceNotFoundException(
                                "Version not found: " + versionNumber); }
                );

        return resumeRepository.save(resume);
    }

    // ══════════════════════════════════════════════════════════
    // DUPLICATE
    // ══════════════════════════════════════════════════════════

    /**
     * Duplicates a resume with a new title.
     * Useful for creating variations for different job applications.
     */
    public Resume duplicateResume(String resumeId, Long userId) {
        log.info("Duplicating resume: {}", resumeId);

        Resume original = getResumeById(resumeId, userId);

        // Check limit before duplicating
        long currentCount = resumeRepository
                .countByUserIdAndStatusNot(
                        String.valueOf(userId), ResumeStatus.ARCHIVED
                );

        Boolean canCreate = userServiceClient
                .canCreateMoreResumes(userId, currentCount)
                .getData();

        if (Boolean.FALSE.equals(canCreate)) {
            throw new ResumeLimitExceededException(
                    "Resume limit reached. Cannot duplicate."
            );
        }

        Resume duplicate = Resume.builder()
                .userId(String.valueOf(userId))
                .title(original.getTitle() + " (Copy)")
                .status(ResumeStatus.DRAFT)
                .templateId(original.getTemplateId())
                .targetJobTitle(original.getTargetJobTitle())
                .targetCompany(original.getTargetCompany())
                .currentVersion(1)
                .sections(deepCopySections(original.getSections()))
                .versions(new ArrayList<>())
                .atsResults(new ArrayList<>())
                .exportedFileIds(new ArrayList<>())
                .isLocked(false)
                .build();

        ResumeVersion initialVersion = ResumeVersion.builder()
                .versionNumber(1)
                .savedBy("DUPLICATE")
                .savedAt(LocalDateTime.now())
                .label("Duplicated from: " + original.getTitle())
                .sections(deepCopySections(original.getSections()))
                .build();

        duplicate.getVersions().add(initialVersion);

        Resume saved = resumeRepository.save(duplicate);
        log.info("Resume duplicated - new id: {}", saved.getId());
        return saved;
    }

    // ══════════════════════════════════════════════════════════
    // STATUS MANAGEMENT
    // ══════════════════════════════════════════════════════════

    public Resume archiveResume(String resumeId, Long userId) {
        log.info("Archiving resume: {}", resumeId);
        Resume resume = getResumeById(resumeId, userId);
        resume.setStatus(ResumeStatus.ARCHIVED);
        return resumeRepository.save(resume);
    }

    public Resume unarchiveResume(String resumeId, Long userId) {
        log.info("Unarchiving resume: {}", resumeId);

        // Need to find archived resume specifically
        Resume resume = resumeRepository.findById(resumeId)
                .filter(r -> r.getUserId().equals(String.valueOf(userId)))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Resume not found: " + resumeId
                ));

        resume.setStatus(ResumeStatus.DRAFT);
        return resumeRepository.save(resume);
    }

    // ══════════════════════════════════════════════════════════
    // WEBSOCKET LOCK
    // ══════════════════════════════════════════════════════════

    // Called when user opens editor — prevents simultaneous edits
    public Resume lockResume(String resumeId, Long userId) {
        Resume resume = getResumeById(resumeId, userId);

        // Check if locked by someone else
        if (Boolean.TRUE.equals(resume.getIsLocked())
                && !resume.getLockedBy().equals(String.valueOf(userId))) {
            throw new UnauthorizedAccessException(
                    "Resume is currently being edited in another session"
            );
        }

        resume.setIsLocked(true);
        resume.setLockedBy(String.valueOf(userId));
        return resumeRepository.save(resume);
    }

    // Called when user closes editor or WebSocket disconnects
    public Resume unlockResume(String resumeId, Long userId) {
        Resume resume = getResumeById(resumeId, userId);
        resume.setIsLocked(false);
        resume.setLockedBy(null);
        return resumeRepository.save(resume);
    }

    // Force unlock — admin or after timeout
    public void forceUnlock(String resumeId) {
        resumeRepository.findById(resumeId).ifPresent(resume -> {
            resume.setIsLocked(false);
            resume.setLockedBy(null);
            resumeRepository.save(resume);
        });
    }

    // ══════════════════════════════════════════════════════════
    // DELETE
    // ══════════════════════════════════════════════════════════

    public void deleteResume(String resumeId, Long userId) {
        log.info("Deleting resume: {}", resumeId);
        Resume resume = getResumeById(resumeId, userId);
        resumeRepository.delete(resume);
        log.info("Resume deleted: {}", resumeId);
    }

    public void deleteAllUserResumes(Long userId) {
        log.info("Deleting all resumes for userId: {}", userId);
        List<Resume> resumes = resumeRepository
                .findByUserId(String.valueOf(userId));
        resumeRepository.deleteAll(resumes);
    }

    // ══════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════

    private List<ResumeSection> buildDefaultSections() {
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

    // Deep copy — changes to copy don't affect original
    // Prevents version history from pointing to same object
    private List<ResumeSection> deepCopySections(List<ResumeSection> sections) {
        if (sections == null) return new ArrayList<>();
        List<ResumeSection> copy = new ArrayList<>();
        for (ResumeSection s : sections) {
            copy.add(ResumeSection.builder()
                    .sectionId(s.getSectionId())
                    .type(s.getType())
                    .title(s.getTitle())
                    .order(s.getOrder())
                    .isVisible(s.getIsVisible())
                    .content(s.getContent() != null
                            ? new HashMap<>(s.getContent())
                            : new HashMap<>())
                    .build());
        }
        return copy;
    }

    private void reorderSections(List<ResumeSection> sections) {
        for (int i = 0; i < sections.size(); i++) {
            sections.get(i).setOrder(i);
        }
    }

    private boolean isResumeComplete(List<ResumeSection> sections) {
        // Resume is complete if PERSONAL_INFO and EXPERIENCE both have content
        return sections.stream()
                .filter(s -> s.getType() == SectionType.PERSONAL_INFO
                        || s.getType() == SectionType.EXPERIENCE)
                .allMatch(s -> s.getContent() != null
                        && !s.getContent().isEmpty());
    }
}
