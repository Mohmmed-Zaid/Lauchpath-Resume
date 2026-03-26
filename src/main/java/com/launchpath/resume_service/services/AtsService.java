package com.launchpath.resume_service.services;


import com.launchpath.resume_service.entity.AtsResult;
import com.launchpath.resume_service.entity.Resume;
import com.launchpath.resume_service.exception.ResourceNotFoundException;
import com.launchpath.resume_service.repo.ResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AtsService {

    private final ResumeRepository resumeRepository;
    private final AiService aiService;
    private final ResumeService resumeService;

    // Max ATS results kept per resume
    private static final int MAX_ATS_RESULTS = 20;

    // ══════════════════════════════════════════════════════════
    // ANALYZE
    // ══════════════════════════════════════════════════════════

    /**
     * Full ATS analysis:
     * 1. Get resume text from sections
     * 2. Call AiService (handles credit + provider selection)
     * 3. Store result in resume document
     * 4. Return result
     */
    public AtsResult analyzeResume(String resumeId,
                                   Long userId,
                                   String jobDescription) {
        log.info("ATS analyze - resumeId: {}, userId: {}", resumeId, userId);

        Resume resume = resumeService.getResumeById(resumeId, userId);

        // Extract plain text from all sections
        String resumeText = extractResumeText(resume);

        if (resumeText.isBlank()) {
            throw new IllegalStateException(
                    "Resume has no content to analyze. Please fill in your sections."
            );
        }

        // Call AI — credit consumed + refunded inside AiService
        AtsResult result = aiService.analyzeResume(
                resumeText, jobDescription, userId
        );

        // Store result in resume document
        resume.getAtsResults().add(result);

        // Trim if over max
        if (resume.getAtsResults().size() > MAX_ATS_RESULTS) {
            resume.getAtsResults().remove(0);
        }

        resumeRepository.save(resume);
        log.info("ATS result stored - resumeId: {}, score: {}",
                resumeId, result.getOverallScore());

        return result;
    }

    // ══════════════════════════════════════════════════════════
    // READ
    // ══════════════════════════════════════════════════════════

    public List<AtsResult> getAtsHistory(String resumeId, Long userId) {
        Resume resume = resumeService.getResumeById(resumeId, userId);
        return resume.getAtsResults();
    }

    public AtsResult getLatestAtsResult(String resumeId, Long userId) {
        Resume resume = resumeService.getResumeById(resumeId, userId);

        List<AtsResult> results = resume.getAtsResults();
        if (results == null || results.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No ATS results found for resume: " + resumeId +
                            ". Run an analysis first."
            );
        }

        return results.get(results.size() - 1);
    }

    // ══════════════════════════════════════════════════════════
    // SUGGESTIONS ONLY — no credit consumed
    // ══════════════════════════════════════════════════════════

    public List<String> getSuggestions(String resumeId,
                                       Long userId,
                                       String jobDescription) {
        log.info("Getting suggestions - resumeId: {}", resumeId);

        Resume resume = resumeService.getResumeById(resumeId, userId);
        String resumeText = extractResumeText(resume);

        return aiService.generateSuggestions(resumeText, jobDescription, userId);
    }

    // ══════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════

    private String extractResumeText(Resume resume) {
        StringBuilder text = new StringBuilder();

        resume.getSections().forEach(section -> {
            if (Boolean.TRUE.equals(section.getIsVisible())
                    && section.getContent() != null
                    && !section.getContent().isEmpty()) {
                text.append(section.getTitle()).append(":\n");
                text.append(section.getContent().toString()).append("\n\n");
            }
        });

        return text.toString().trim();
    }
}
