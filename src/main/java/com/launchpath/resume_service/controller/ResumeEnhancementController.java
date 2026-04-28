package com.launchpath.resume_service.controller;

import com.launchpath.resume_service.dto.request.CareerChatRequestDTO;
import com.launchpath.resume_service.dto.request.CareerGuidanceRequestDTO;
import com.launchpath.resume_service.dto.request.OptimizeResumeRequestDTO;
import com.launchpath.resume_service.dto.request.RewriteSectionRequestDTO;
import com.launchpath.resume_service.dto.response.*;
import com.launchpath.resume_service.exception.RateLimitExceededException;
import com.launchpath.resume_service.services.RateLimitService;
import com.launchpath.resume_service.services.ResumeEnhancementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/resumes")
@RequiredArgsConstructor
public class ResumeEnhancementController {

    private final ResumeEnhancementService enhancementService;
    private final RateLimitService rateLimitService;
    // ══════════════════════════════════════════════════════
    // OPTIMIZE RESUME
    // POST /api/v1/resumes/{resumeId}/optimize
    // ══════════════════════════════════════════════════════

    @PostMapping("/{resumeId}/optimize")
    public ResponseEntity<ApiResponseDTO<OptimizeResumeResponseDTO>>
    optimizeResume(
            @PathVariable String resumeId,
            @RequestBody(required = false)
            OptimizeResumeRequestDTO request) {
        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException(
                    "Too many requests. Please wait 1 minute."
            );
        }

        log.info("Optimize resume - id: {}, userId: {}", resumeId, userId);

        if (request == null) {
            request = new OptimizeResumeRequestDTO();
        }

        OptimizeResumeResponseDTO result =
                enhancementService.optimizeResume(resumeId, userId, request);

        return ResponseEntity.ok(
                ApiResponseDTO.success(
                        "Resume optimized successfully", result
                )
        );
    }

    // ══════════════════════════════════════════════════════
    // REWRITE FOR JOB
    // POST /api/v1/resumes/{resumeId}/rewrite
    // ══════════════════════════════════════════════════════

    @PostMapping("/{resumeId}/rewrite")
    public ResponseEntity<ApiResponseDTO<RewriteSectionResponseDTO>>
    rewriteForJob(
            @PathVariable String resumeId,
            @Valid @RequestBody RewriteSectionRequestDTO request) {

        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException(
                    "Too many requests. Please wait 1 minute."
            );
        }
        log.info("Rewrite resume - id: {}, userId: {}", resumeId, userId);

        RewriteSectionResponseDTO result =
                enhancementService.rewriteForJob(resumeId, userId, request);

        return ResponseEntity.ok(
                ApiResponseDTO.success(
                        "Resume rewritten for job successfully", result
                )
        );
    }

    // ══════════════════════════════════════════════════════
    // CAREER GUIDANCE
    // POST /api/v1/resumes/{resumeId}/career-guidance
    // ══════════════════════════════════════════════════════

    @PostMapping("/{resumeId}/career-guidance")
    public ResponseEntity<ApiResponseDTO<CareerGuidanceResponseDTO>>
    getCareerGuidance(
            @PathVariable String resumeId,
            @RequestBody(required = false)
            CareerGuidanceRequestDTO request) {

        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException(
                    "Too many requests. Please wait 1 minute."
            );
        }
        log.info("Career guidance - resumeId: {}, userId: {}",
                resumeId, userId);

        if (request == null) {
            request = new CareerGuidanceRequestDTO();
        }

        CareerGuidanceResponseDTO result =
                enhancementService.getCareerGuidance(resumeId, userId, request);

        return ResponseEntity.ok(
                ApiResponseDTO.success(
                        "Career guidance generated successfully", result
                )
        );
    }

    @PostMapping("/{resumeId}/chat")
    public ResponseEntity<ApiResponseDTO<CareerChatResponseDTO>> careerChat(
            @PathVariable String resumeId,
            @Valid @RequestBody CareerChatRequestDTO request) {
        Long userId = getCurrentUserId();

        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException(
                    "Too many requests. Please wait."
            );
        }
        CareerChatResponseDTO result =
                enhancementService.careerChat(resumeId, userId, request);

        return ResponseEntity.ok(
                ApiResponseDTO.success("Chat response", result)
        );
    }

    // ── Private ───────────────────────────────────────────

    private Long getCurrentUserId() {
        return (Long) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
    }
}