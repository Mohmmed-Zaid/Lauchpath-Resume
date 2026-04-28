package com.launchpath.resume_service.controller;

import com.launchpath.resume_service.exception.RateLimitExceededException;
import com.launchpath.resume_service.services.RateLimitService;
import jakarta.validation.Valid;
import com.launchpath.resume_service.entity.Resume;
import com.launchpath.resume_service.entity.ResumeSection;
import com.launchpath.resume_service.dto.request.*;
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
@RequestMapping("/api/v1/resumes/{resumeId}/sections")
@RequiredArgsConstructor
public class SectionController {

    private final ResumeService resumeService;
    private final ResumeMapper resumeMapper;
    private final RateLimitService rateLimitService;

    // ══════════════════════════════════════════════════════════
    // GET ALL SECTIONS
    // GET /api/v1/resumes/{resumeId}/sections
    // ══════════════════════════════════════════════════════════

    @GetMapping
    public ResponseEntity<ApiResponseDTO<List<ResumeSectionResponseDTO>>> getSections(
            @PathVariable String resumeId) {
        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException(
                    "Too many requests. Please wait 1 minute."
            );
        }
        log.debug("Get sections - resumeId: {}", resumeId);

        Resume resume = resumeService.getResumeById(resumeId, userId);

        List<ResumeSectionResponseDTO> sections = resume.getSections()
                .stream()
                .map(resumeMapper::toSectionDTO)
                .toList();

        return ResponseEntity.ok(
                ApiResponseDTO.success("Sections fetched", sections)
        );
    }

    // ══════════════════════════════════════════════════════════
    // ADD SECTION
    // POST /api/v1/resumes/{resumeId}/sections
    // ══════════════════════════════════════════════════════════

    @PostMapping
    public ResponseEntity<ApiResponseDTO<ResumeDetailResponseDTO>> addSection(
            @PathVariable String resumeId,
            @Valid @RequestBody AddSectionRequestDTO request) {
        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException(
                    "Too many requests. Please wait 1 minute."
            );
        }
        log.info("Add section - resumeId: {}, type: {}",
                resumeId, request.getType());

        ResumeSection section = ResumeSection.builder()
                .type(request.getType())
                .title(request.getTitle())
                .content(request.getContent())
                .build();

        Resume updated = resumeService.addSection(resumeId, userId, section);

        return ResponseEntity.ok(
                ApiResponseDTO.success(
                        "Section added successfully",
                        resumeMapper.toDetailDTO(updated, userId)
                )
        );
    }

    // ══════════════════════════════════════════════════════════
    // UPDATE SECTION CONTENT
    // PUT /api/v1/resumes/{resumeId}/sections/{sectionId}
    // ══════════════════════════════════════════════════════════

    @PutMapping("/{sectionId}")
    public ResponseEntity<ApiResponseDTO<ResumeSectionResponseDTO>> updateSection(
            @PathVariable String resumeId,
            @PathVariable String sectionId,
            @Valid @RequestBody UpdateSectionRequestDTO request) {
        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException(
                    "Too many requests. Please wait 1 minute."
            );
        }
        log.info("Update section - resumeId: {}, sectionId: {}",
                resumeId, sectionId);

        Resume updated = resumeService.updateSection(
                resumeId, userId, sectionId, request.getContent()
        );

        // Return updated section only
        ResumeSection updatedSection = updated.getSections()
                .stream()
                .filter(s -> s.getSectionId().equals(sectionId))
                .findFirst()
                .orElseThrow();

        return ResponseEntity.ok(
                ApiResponseDTO.success(
                        "Section updated successfully",
                        resumeMapper.toSectionDTO(updatedSection)
                )
        );
    }

    // ══════════════════════════════════════════════════════════
    // REORDER SECTION
    // PATCH /api/v1/resumes/{resumeId}/sections/{sectionId}/reorder
    // ══════════════════════════════════════════════════════════

    @PatchMapping("/{sectionId}/reorder")
    public ResponseEntity<ApiResponseDTO<List<ResumeSectionResponseDTO>>> reorderSection(
            @PathVariable String resumeId,
            @PathVariable String sectionId,
            @Valid @RequestBody ReorderSectionRequestDTO request) {

        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException(
                    "Too many requests. Please wait 1 minute."
            );
        }
        log.info("Reorder section - sectionId: {}, newOrder: {}",
                sectionId, request.getNewOrder());

        Resume updated = resumeService.reorderSection(
                resumeId, userId, sectionId, request.getNewOrder()
        );

        List<ResumeSectionResponseDTO> sections = updated.getSections()
                .stream()
                .map(resumeMapper::toSectionDTO)
                .toList();

        return ResponseEntity.ok(
                ApiResponseDTO.success("Section reordered", sections)
        );
    }

    // ══════════════════════════════════════════════════════════
    // TOGGLE VISIBILITY
    // PATCH /api/v1/resumes/{resumeId}/sections/{sectionId}/visibility
    // ══════════════════════════════════════════════════════════

    @PatchMapping("/{sectionId}/visibility")
    public ResponseEntity<ApiResponseDTO<ResumeSectionResponseDTO>> toggleVisibility(
            @PathVariable String resumeId,
            @PathVariable String sectionId) {

        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException(
                    "Too many requests. Please wait 1 minute."
            );
        }
        log.info("Toggle visibility - sectionId: {}", sectionId);

        Resume updated = resumeService.toggleSectionVisibility(
                resumeId, userId, sectionId
        );

        ResumeSection section = updated.getSections()
                .stream()
                .filter(s -> s.getSectionId().equals(sectionId))
                .findFirst()
                .orElseThrow();

        return ResponseEntity.ok(
                ApiResponseDTO.success(
                        "Section visibility toggled",
                        resumeMapper.toSectionDTO(section)
                )
        );
    }

    // ══════════════════════════════════════════════════════════
    // DELETE SECTION
    // DELETE /api/v1/resumes/{resumeId}/sections/{sectionId}
    // ══════════════════════════════════════════════════════════

    @DeleteMapping("/{sectionId}")
    public ResponseEntity<ApiResponseDTO<Void>> deleteSection(
            @PathVariable String resumeId,
            @PathVariable String sectionId) {
        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException(
                    "Too many requests. Please wait 1 minute."
            );
        }
        log.info("Delete section - sectionId: {}", sectionId);

        resumeService.deleteSection(resumeId, userId, sectionId);

        return ResponseEntity.ok(
                ApiResponseDTO.success("Section deleted successfully")
        );
    }
    private Long getCurrentUserId() {
        return (Long) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
    }
}