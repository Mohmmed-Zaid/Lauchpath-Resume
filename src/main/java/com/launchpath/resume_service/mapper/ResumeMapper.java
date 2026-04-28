package com.launchpath.resume_service.mapper;

//All entity → DTO conversions


import com.launchpath.resume_service.dto.response.*;
import com.launchpath.resume_service.entity.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ResumeMapper {

    // ── Resume → Summary (dashboard list) ────────────────────
    public ResumeSummaryResponseDTO toSummaryDTO(Resume resume) {

        // Latest ATS score — null if never analyzed
        Integer latestScore = null;
        if (resume.getAtsResults() != null
                && !resume.getAtsResults().isEmpty()) {
            latestScore = resume.getAtsResults()
                    .get(resume.getAtsResults().size() - 1)
                    .getOverallScore();
        }

        return ResumeSummaryResponseDTO.builder()
                .id(resume.getId())
                .title(resume.getTitle())
                .status(resume.getStatus())
                .templateId(resume.getTemplateId())
                .targetJobTitle(resume.getTargetJobTitle())
                .targetCompany(resume.getTargetCompany())
                .currentVersion(resume.getCurrentVersion())
                .sectionsCount(resume.getSections() != null
                        ? resume.getSections().size() : 0)
                .atsResultsCount(resume.getAtsResults() != null
                        ? resume.getAtsResults().size() : 0)
                .latestAtsScore(latestScore)
                .isLocked(resume.getIsLocked())
                .createdAt(resume.getCreatedAt())
                .updatedAt(resume.getUpdatedAt())
                .build();
    }

    // ── Resume → Detail (editor) ──────────────────────────────
    public ResumeDetailResponseDTO toDetailDTO(Resume resume, Long requestingUserId) {

        // Version history — lightweight, no section data
        List<ResumeDetailResponseDTO.VersionSummaryDTO> versionSummaries =
                resume.getVersions() != null
                        ? resume.getVersions().stream()
                        .map(v -> ResumeDetailResponseDTO.VersionSummaryDTO.builder()
                                .versionNumber(v.getVersionNumber())
                                .savedBy(v.getSavedBy())
                                .label(v.getLabel())
                                .savedAt(v.getSavedAt())
                                .build())
                        .collect(Collectors.toList())
                        : List.of();

        // Latest ATS result only
        AtsResult latestAts = null;
        if (resume.getAtsResults() != null
                && !resume.getAtsResults().isEmpty()) {
            latestAts = resume.getAtsResults()
                    .get(resume.getAtsResults().size() - 1);
        }

        return ResumeDetailResponseDTO.builder()
                .id(resume.getId())
                .userId(resume.getUserId())
                .title(resume.getTitle())
                .status(resume.getStatus())
                .templateId(resume.getTemplateId())
                .targetJobTitle(resume.getTargetJobTitle())
                .targetCompany(resume.getTargetCompany())
                .currentVersion(resume.getCurrentVersion())
                .isLocked(resume.getIsLocked())
                .isLockedByMe(
                        Boolean.TRUE.equals(resume.getIsLocked())
                                && resume.getLockedBy() != null
                                && resume.getLockedBy()
                                .equals(String.valueOf(requestingUserId))
                )
                .sections(resume.getSections())
                .versionHistory(versionSummaries)
                .latestAtsResult(latestAts)
                .createdAt(resume.getCreatedAt())
                .updatedAt(resume.getUpdatedAt())
                .build();
    }

    // ── Section → DTO ─────────────────────────────────────────
    public ResumeSectionResponseDTO toSectionDTO(ResumeSection section) {
        return ResumeSectionResponseDTO.builder()
                .sectionId(section.getSectionId())
                .type(section.getType())
                .title(section.getTitle())
                .order(section.getOrder())
                .isVisible(section.getIsVisible())
                .content(section.getContent())
                .build();
    }

    // ── AtsResult → DTO ───────────────────────────────────────
    public AtsResultResponseDTO toAtsResultDTO(AtsResult result) {
        return AtsResultResponseDTO.builder()
                .resultId(result.getResultId())
                .provider(result.getProvider())
                .analyzedAt(result.getAnalyzedAt())
                .jobDescription(result.getJobDescription())
                .overallScore(result.getOverallScore())
                .metricScores(result.getMetricScores())
                .suggestions(result.getSuggestions())
                .matchedKeywords(result.getMatchedKeywords())
                .missingKeywords(result.getMissingKeywords())
                .scoreLabel(getScoreLabel(result.getOverallScore()))
                .build();
    }

    // ── Template → DTO ────────────────────────────────────────
    public TemplateResponseDTO toTemplateDTO(Template template) {
        return TemplateResponseDTO.builder()
                .id(template.getId())
                .name(template.getName())
                .description(template.getDescription())
                .category(template.getCategory())
                .isPremium(template.getIsPremium())
                .previewUrl(template.getPreviewUrl())
                .primaryColor(template.getPrimaryColor())
                .secondaryColor(template.getSecondaryColor())
                .fontFamily(template.getFontFamily())
                .usageCount(template.getUsageCount())
                .sectionStructure(template.getSectionStructure())
                .build();
    }

    // ── ParsedResume → DTO ────────────────────────────────────
    public ParsedResumeResponseDTO toParsedResumeDTO(ParsedResume parsed) {
        return ParsedResumeResponseDTO.builder()
                .id(parsed.getId())
                .originalFileName(parsed.getOriginalFileName())
                .isParsed(parsed.getIsParsed())
                .parseError(parsed.getParseError())
                .parsedSections(parsed.getParsedSections())
                // Only send raw text if AI parsing failed
                .rawText(!Boolean.TRUE.equals(parsed.getIsParsed())
                        ? parsed.getRawText()
                        : null)
                .build();
    }

    // ── Version → Detail DTO ──────────────────────────────────
    public VersionDetailResponseDTO toVersionDetailDTO(ResumeVersion version) {
        return VersionDetailResponseDTO.builder()
                .versionNumber(version.getVersionNumber())
                .savedBy(version.getSavedBy())
                .label(version.getLabel())
                .savedAt(version.getSavedAt())
                .sections(version.getSections())
                .build();
    }

    // ── Export Map → DTO ──────────────────────────────────────
    public ExportResponseDTO toExportDTO(java.util.Map<String, Object> result) {
        return ExportResponseDTO.builder()
                .url((String) result.get("url"))
                .format((String) result.get("format"))
                .cloudinaryId((String) result.get("cloudinaryId"))
                .resumeTitle((String) result.get("resumeTitle"))
                .build();
    }

    // ── Private Helper ────────────────────────────────────────
    private String getScoreLabel(Integer score) {
        if (score == null) return "UNKNOWN";
        if (score >= 80)   return "EXCELLENT";
        if (score >= 60)   return "GOOD";
        if (score >= 40)   return "FAIR";
        return "POOR";
    }
}
