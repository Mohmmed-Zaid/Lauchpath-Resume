package com.launchpath.resume_service.controller;

import com.launchpath.resume_service.exception.RateLimitExceededException;
import com.launchpath.resume_service.exception.ResourceNotFoundException;
import com.launchpath.resume_service.services.RateLimitService;
import jakarta.validation.Valid;
import com.launchpath.resume_service.entity.Resume;
import com.launchpath.resume_service.entity.ResumeVersion;
import com.launchpath.resume_service.dto.request.LabelVersionRequestDTO;
import com.launchpath.resume_service.dto.response.*;
import com.launchpath.resume_service.mapper.ResumeMapper;
import com.launchpath.resume_service.services.ResumeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/resumes/{resumeId}/versions")
@RequiredArgsConstructor
public class VersionController {

    private final ResumeService resumeService;
    private final ResumeMapper resumeMapper;
    private final RateLimitService rateLimitService;


    // ══════════════════════════════════════════════════════════
    // GET ALL VERSIONS — SUMMARY
    // GET /api/v1/resumes/{resumeId}/versions
    // ══════════════════════════════════════════════════════════

    @GetMapping
    public ResponseEntity<ApiResponseDTO<List<ResumeDetailResponseDTO.VersionSummaryDTO>>>
    getVersionHistory(
            @PathVariable String resumeId){
        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException(
                    "Too many requests. Please wait 1 minute."
            );
        }
        log.debug("Get version history - resumeId: {}", resumeId);

        List<ResumeDetailResponseDTO.VersionSummaryDTO> versions =
                resumeService.getVersionHistory(resumeId, userId)
                        .stream()
                        .map(v -> ResumeDetailResponseDTO.VersionSummaryDTO.builder()
                                .versionNumber(v.getVersionNumber())
                                .savedBy(v.getSavedBy())
                                .label(v.getLabel())
                                .savedAt(v.getSavedAt())
                                .build())
                        .toList();

        return ResponseEntity.ok(
                ApiResponseDTO.success("Version history fetched", versions)
        );
    }

    // ══════════════════════════════════════════════════════════
    // GET VERSION DETAIL — WITH SECTIONS
    // GET /api/v1/resumes/{resumeId}/versions/{versionNumber}
    // Shows full section content for that version
    // ══════════════════════════════════════════════════════════

    @GetMapping("/{versionNumber}")
    public ResponseEntity<ApiResponseDTO<VersionDetailResponseDTO>> getVersionDetail(
            @PathVariable String resumeId,
            @PathVariable int versionNumber){
        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException(
                    "Too many requests. Please wait 1 minute."
            );
        }
        log.debug("Get version detail - resumeId: {}, version: {}",
                resumeId, versionNumber);

        List<ResumeVersion> versions = resumeService
                .getVersionHistory(resumeId, userId);

        ResumeVersion version = versions.stream()
                .filter(v -> v.getVersionNumber() == versionNumber)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Version not found: " + versionNumber
                ));

        return ResponseEntity.ok(
                ApiResponseDTO.success(
                        "Version fetched",
                        resumeMapper.toVersionDetailDTO(version)
                )
        );
    }

    // ══════════════════════════════════════════════════════════
    // RESTORE VERSION
    // POST /api/v1/resumes/{resumeId}/versions/{versionNumber}/restore
    // Current state saved before restoring — user never loses work
    // ══════════════════════════════════════════════════════════

    @PostMapping("/{versionNumber}/restore")
    public ResponseEntity<ApiResponseDTO<ResumeDetailResponseDTO>> restoreVersion(
            @PathVariable String resumeId,
            @PathVariable int versionNumber) {
        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException(
                    "Too many requests. Please wait 1 minute."
            );
        }
        log.info("Restore version - resumeId: {}, version: {}",
                resumeId, versionNumber);

        Resume restored = resumeService.restoreVersion(
                resumeId, userId, versionNumber
        );

        return ResponseEntity.ok(
                ApiResponseDTO.success(
                        "Version " + versionNumber + " restored successfully",
                        resumeMapper.toDetailDTO(restored, userId)
                )
        );
    }

    // ══════════════════════════════════════════════════════════
    // LABEL VERSION
    // PATCH /api/v1/resumes/{resumeId}/versions/{versionNumber}/label
    // e.g. "Google Application", "Final v2"
    // ══════════════════════════════════════════════════════════

    @PatchMapping("/{versionNumber}/label")
    public ResponseEntity<ApiResponseDTO<Void>> labelVersion(
            @PathVariable String resumeId,
            @PathVariable int versionNumber,
            @Valid @RequestBody LabelVersionRequestDTO request) {
        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException(
                    "Too many requests. Please wait 1 minute."
            );
        }
        log.info("Label version - resumeId: {}, version: {}, label: {}",
                resumeId, versionNumber, request.getLabel());

        resumeService.labelVersion(
                resumeId, userId, versionNumber, request.getLabel()
        );

        return ResponseEntity.ok(
                ApiResponseDTO.success("Version labelled successfully")
        );
    }
    private Long getCurrentUserId() {
        return (Long) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
    }
}
