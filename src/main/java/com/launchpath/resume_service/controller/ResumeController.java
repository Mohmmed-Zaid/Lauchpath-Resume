package com.launchpath.resume_service.controller;

import com.launchpath.resume_service.dto.request.*;
import com.launchpath.resume_service.dto.response.*;
import com.launchpath.resume_service.entity.Resume;
import com.launchpath.resume_service.entity.ResumeVersion;
import com.launchpath.resume_service.exception.RateLimitExceededException;
import com.launchpath.resume_service.mapper.ResumeMapper;
import com.launchpath.resume_service.services.ResumeEnhancementService;
import com.launchpath.resume_service.services.ResumeService;
import com.launchpath.resume_service.services.RateLimitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/resumes")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;
    private final ResumeMapper resumeMapper;
    private final RateLimitService rateLimitService;
    private final ResumeEnhancementService enhancementService;

    // ══════════════════════════════════════════════════════════
    // GENERATE WITH AI WIZARD
    // POST /api/v1/resumes/generate
    //
    // FIX: This now accepts the full wizard payload (fullName,
    // targetRole, experience, education, skills, projects) and
    // delegates to ResumeEnhancementService.generateResumeFromText
    // which calls Groq AI and builds real populated sections.
    //
    // The old endpoint only accepted title/templateId/targetJobTitle
    // and created a resume with 6 empty sections — that's why the
    // editor always showed "No visible sections".
    // ══════════════════════════════════════════════════════════

    @PostMapping("/generate")
    public ResponseEntity<ApiResponseDTO<ResumeDetailResponseDTO>> generateResume(
            @Valid @RequestBody GenerateResumeRequestDTO request) {

        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException("Too many requests. Please wait 1 minute.");
        }
        log.info("Generate resume with AI - userId: {}, targetRole: {}",
                userId, request.getTargetRole());

        // Delegates to AI — returns resume with fully populated sections
        Resume resume = enhancementService.generateResumeFromText(userId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponseDTO.success(
                        "Resume generated successfully",
                        resumeMapper.toDetailDTO(resume, userId)
                ));
    }

    // ══════════════════════════════════════════════════════════
    // CREATE BLANK (scratch)
    // POST /api/v1/resumes/create
    // Creates resume with 6 empty default sections (intentionally blank)
    // ══════════════════════════════════════════════════════════

    @PostMapping("/create")
    public ResponseEntity<ApiResponseDTO<ResumeDetailResponseDTO>> createResume(
            @Valid @RequestBody CreateResumeRequestDTO request) {

        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException("Too many requests. Please wait 1 minute.");
        }
        log.info("Create blank resume - userId: {}", userId);

        Resume resume = resumeService.createResume(
                userId,
                request.getTitle(),
                request.getTemplateId(),
                request.getTargetJobTitle()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponseDTO.success(
                        "Resume created successfully",
                        resumeMapper.toDetailDTO(resume, userId)
                ));
    }

    // ══════════════════════════════════════════════════════════
    // GET ALL — SUMMARY LIST
    // GET /api/v1/resumes
    // ══════════════════════════════════════════════════════════

    @GetMapping
    public ResponseEntity<ApiResponseDTO<List<ResumeSummaryResponseDTO>>> getAllResumes() {
        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException("Too many requests. Please wait 1 minute.");
        }

        List<ResumeSummaryResponseDTO> summaries = resumeService
                .getResumeSummaries(userId)
                .stream()
                .map(resumeMapper::toSummaryDTO)
                .toList();

        return ResponseEntity.ok(
                ApiResponseDTO.success("Resumes fetched successfully", summaries)
        );
    }

    // ══════════════════════════════════════════════════════════
    // GET ALL — PAGINATED
    // GET /api/v1/resumes/paginated?page=0&size=10
    // ══════════════════════════════════════════════════════════

    @GetMapping("/paginated")
    public ResponseEntity<ApiResponseDTO<Page<ResumeSummaryResponseDTO>>> getResumesPaginated(
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {

        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException("Too many requests. Please wait 1 minute.");
        }

        Page<ResumeSummaryResponseDTO> page = resumeService
                .getResumesPaginated(userId, pageable)
                .map(resumeMapper::toSummaryDTO);

        return ResponseEntity.ok(ApiResponseDTO.success("Resumes fetched", page));
    }

    // ══════════════════════════════════════════════════════════
    // GET SINGLE — FULL DETAIL
    // GET /api/v1/resumes/{id}
    // ══════════════════════════════════════════════════════════

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDTO<ResumeDetailResponseDTO>> getResume(
            @PathVariable String id) {

        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException("Too many requests. Please wait 1 minute.");
        }

        Resume resume = resumeService.getResumeById(id, userId);

        return ResponseEntity.ok(
                ApiResponseDTO.success("Resume fetched successfully",
                        resumeMapper.toDetailDTO(resume, userId))
        );
    }

    // ══════════════════════════════════════════════════════════
    // GET RESUME COUNT
    // GET /api/v1/resumes/count
    // ══════════════════════════════════════════════════════════

    @GetMapping("/count")
    public ResponseEntity<ApiResponseDTO<Long>> getResumeCount() {
        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException("Too many requests. Please wait 1 minute.");
        }
        long count = resumeService.getResumeCount(userId);
        return ResponseEntity.ok(ApiResponseDTO.success("Resume count fetched", count));
    }

    // ══════════════════════════════════════════════════════════
    // SAVE — FULL RESUME
    // PUT /api/v1/resumes/{id}
    // ══════════════════════════════════════════════════════════

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponseDTO<ResumeDetailResponseDTO>> saveResume(
            @PathVariable String id,
            @Valid @RequestBody SaveResumeRequestDTO request) {

        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException("Too many requests. Please wait 1 minute.");
        }

        Resume saved = resumeService.saveResume(
                id, userId, request.getSections(), request.getSavedBy()
        );

        return ResponseEntity.ok(
                ApiResponseDTO.success("Resume saved successfully",
                        resumeMapper.toDetailDTO(saved, userId))
        );
    }

    // ══════════════════════════════════════════════════════════
    // AUTO SAVE
    // PUT /api/v1/resumes/{id}/autosave
    // ══════════════════════════════════════════════════════════

    @PutMapping("/{id}/autosave")
    public ResponseEntity<ApiResponseDTO<Void>> autoSave(
            @PathVariable String id,
            @Valid @RequestBody SaveResumeRequestDTO request) {

        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException("Too many requests. Please wait 1 minute.");
        }

        resumeService.autoSave(id, userId, request.getSections());
        return ResponseEntity.ok(ApiResponseDTO.success("Auto-saved successfully"));
    }

    // ══════════════════════════════════════════════════════════
    // UPDATE METADATA
    // PATCH /api/v1/resumes/{id}/metadata
    // ══════════════════════════════════════════════════════════

    @PatchMapping("/{id}/metadata")
    public ResponseEntity<ApiResponseDTO<ResumeDetailResponseDTO>> updateMetadata(
            @PathVariable String id,
            @Valid @RequestBody UpdateResumeMetadataRequestDTO request) {

        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException("Too many requests. Please wait 1 minute.");
        }

        Resume updated = resumeService.updateResumeMetadata(
                id, userId,
                request.getTitle(),
                request.getTargetJobTitle(),
                request.getTargetCompany(),
                request.getTemplateId()
        );

        return ResponseEntity.ok(
                ApiResponseDTO.success("Resume metadata updated",
                        resumeMapper.toDetailDTO(updated, userId))
        );
    }

    // ══════════════════════════════════════════════════════════
    // DUPLICATE
    // POST /api/v1/resumes/{id}/duplicate
    // ══════════════════════════════════════════════════════════

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<ApiResponseDTO<ResumeDetailResponseDTO>> duplicate(
            @PathVariable String id) {

        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException("Too many requests. Please wait 1 minute.");
        }

        Resume duplicate = resumeService.duplicateResume(id, userId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponseDTO.success("Resume duplicated successfully",
                        resumeMapper.toDetailDTO(duplicate, userId)));
    }

    // ══════════════════════════════════════════════════════════
    // ARCHIVE / UNARCHIVE
    // ══════════════════════════════════════════════════════════

    @PatchMapping("/{id}/archive")
    public ResponseEntity<ApiResponseDTO<Void>> archive(@PathVariable String id) {
        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException("Too many requests. Please wait 1 minute.");
        }
        resumeService.archiveResume(id, userId);
        return ResponseEntity.ok(ApiResponseDTO.success("Resume archived successfully"));
    }

    @PatchMapping("/{id}/unarchive")
    public ResponseEntity<ApiResponseDTO<Void>> unarchive(@PathVariable String id) {
        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException("Too many requests. Please wait 1 minute.");
        }
        resumeService.unarchiveResume(id, userId);
        return ResponseEntity.ok(ApiResponseDTO.success("Resume unarchived successfully"));
    }

    // ══════════════════════════════════════════════════════════
    // DELETE
    // DELETE /api/v1/resumes/{id}
    // ══════════════════════════════════════════════════════════

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseDTO<Void>> deleteResume(@PathVariable String id) {
        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException("Too many requests. Please wait 1 minute.");
        }
        resumeService.deleteResume(id, userId);
        return ResponseEntity.ok(ApiResponseDTO.success("Resume deleted successfully"));
    }

    // ══════════════════════════════════════════════════════════
    // LOCK / UNLOCK
    // ══════════════════════════════════════════════════════════

    @PostMapping("/{id}/lock")
    public ResponseEntity<ApiResponseDTO<Void>> lock(@PathVariable String id) {
        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException("Too many requests. Please wait 1 minute.");
        }
        resumeService.lockResume(id, userId);
        return ResponseEntity.ok(ApiResponseDTO.success("Resume locked for editing"));
    }

    @PostMapping("/{id}/unlock")
    public ResponseEntity<ApiResponseDTO<Void>> unlock(@PathVariable String id) {
        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException("Too many requests. Please wait 1 minute.");
        }
        resumeService.unlockResume(id, userId);
        return ResponseEntity.ok(ApiResponseDTO.success("Resume unlocked"));
    }

    private Long getCurrentUserId() {
        return (Long) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
    }
}