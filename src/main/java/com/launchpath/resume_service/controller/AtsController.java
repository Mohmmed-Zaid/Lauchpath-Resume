package com.launchpath.resume_service.controller;

import com.launchpath.resume_service.dto.request.*;
import com.launchpath.resume_service.dto.response.*;
import com.launchpath.resume_service.dto.response.AtsResultResponseDTO;
import com.launchpath.resume_service.entity.AtsResult;
import com.launchpath.resume_service.exception.RateLimitExceededException;
import com.launchpath.resume_service.mapper.ResumeMapper;
import com.launchpath.resume_service.services.AtsService;
import com.launchpath.resume_service.services.RateLimitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/resumes/{resumeId}/ats")
@RequiredArgsConstructor
public class AtsController {

    private final AtsService atsService;
    private final ResumeMapper resumeMapper;
    private final RateLimitService rateLimitService;
    // ══════════════════════════════════════════════════════════
    // ANALYZE
    // POST /api/v1/resumes/{resumeId}/ats/analyze
    // Consumes 1 ATS credit — refunded on failure
    // ══════════════════════════════════════════════════════════

    @PostMapping("/analyze")
    public ResponseEntity<ApiResponseDTO<AtsResultResponseDTO>> analyze(
            @PathVariable String resumeId,
            @RequestBody(required = false) AtsAnalyzeRequestDTO request) {

        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException(
                    "Too many requests. Please wait 1 minute."
            );
        }
        log.info("ATS analyze - resumeId: {}, userId: {}", resumeId, userId);

        String jobDescription = request != null
                ? request.getJobDescription()
                : null;

        AtsResult result = atsService.analyzeResume(
                resumeId, userId, jobDescription
        );

        return ResponseEntity.ok(
                ApiResponseDTO.success(
                        "ATS analysis complete",
                        resumeMapper.toAtsResultDTO(result)
                )
        );
    }

    // ══════════════════════════════════════════════════════════
    // GET LATEST RESULT
    // GET /api/v1/resumes/{resumeId}/ats/latest
    // ══════════════════════════════════════════════════════════

    @GetMapping("/latest")
    public ResponseEntity<ApiResponseDTO<AtsResultResponseDTO>> getLatest(
            @PathVariable String resumeId){
        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException(
                    "Too many requests. Please wait 1 minute."
            );
        }

        log.debug("Get latest ATS - resumeId: {}", resumeId);

        AtsResult result = atsService.getLatestAtsResult(resumeId, userId);

        return ResponseEntity.ok(
                ApiResponseDTO.success(
                        "Latest ATS result fetched",
                        resumeMapper.toAtsResultDTO(result)
                )
        );
    }

    // ══════════════════════════════════════════════════════════
    // GET HISTORY
    // GET /api/v1/resumes/{resumeId}/ats/history
    // All past analyses
    // ══════════════════════════════════════════════════════════

    @GetMapping("/history")
    public ResponseEntity<ApiResponseDTO<List<AtsResultResponseDTO>>> getHistory(
            @PathVariable String resumeId){

        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException(
                    "Too many requests. Please wait 1 minute."
            );
        }
        log.debug("Get ATS history - resumeId: {}", resumeId);

        List<AtsResultResponseDTO> history = atsService
                .getAtsHistory(resumeId, userId)
                .stream()
                .map(resumeMapper::toAtsResultDTO)
                .toList();

        return ResponseEntity.ok(
                ApiResponseDTO.success("ATS history fetched", history)
        );
    }

    // ══════════════════════════════════════════════════════════
    // GET SUGGESTIONS ONLY
    // POST /api/v1/resumes/{resumeId}/ats/suggestions
    // No credit consumed — just AI improvement tips
    // ══════════════════════════════════════════════════════════

    @PostMapping("/suggestions")
    public ResponseEntity<ApiResponseDTO<List<String>>> getSuggestions(
            @PathVariable String resumeId,
            @RequestBody(required = false) AtsAnalyzeRequestDTO request) {
        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException(
                    "Too many requests. Please wait 1 minute."
            );
        }
        log.info("Get suggestions - resumeId: {}", resumeId);

        String jobDescription = request != null
                ? request.getJobDescription()
                : null;

        List<String> suggestions = atsService.getSuggestions(
                resumeId, userId, jobDescription
        );

        return ResponseEntity.ok(
                ApiResponseDTO.success(
                        "Suggestions generated successfully",
                        suggestions
                )
        );
    }
    private Long getCurrentUserId() {
        return (Long) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
    }
}
