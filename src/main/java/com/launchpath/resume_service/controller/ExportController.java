package com.launchpath.resume_service.controller;

import com.launchpath.resume_service.dto.request.ExportRequestDTO;
import com.launchpath.resume_service.dto.response.ApiResponseDTO;
import com.launchpath.resume_service.dto.response.ExportResponseDTO;
import com.launchpath.resume_service.exception.RateLimitExceededException;
import com.launchpath.resume_service.mapper.ResumeMapper;
import com.launchpath.resume_service.services.ExportService;
import com.launchpath.resume_service.services.RateLimitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/resumes/{resumeId}/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;
    private final ResumeMapper resumeMapper;
    private final RateLimitService rateLimitService;

    // ══════════════════════════════════════════════════════════
    // EXPORT RESUME
    // POST /api/v1/resumes/{resumeId}/export
    // Consumes 1 download credit
    // Returns Cloudinary download URL
    // ══════════════════════════════════════════════════════════

    @PostMapping
    public ResponseEntity<ApiResponseDTO<ExportResponseDTO>> exportResume(
            @PathVariable String resumeId,
            @Valid @RequestBody ExportRequestDTO request) {
        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException(
                    "Too many requests. Please wait 1 minute."
            );
        }
        log.info("Export resume - id: {}, format: {}, userId: {}",
                resumeId, request.getFormat(), userId);

        Map<String, Object> result = exportService.exportResume(
                resumeId,
                userId,
                request.getFormat()
        );

        return ResponseEntity.ok(
                ApiResponseDTO.success(
                        request.getFormat().name()
                                + " exported successfully",
                        resumeMapper.toExportDTO(result)
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