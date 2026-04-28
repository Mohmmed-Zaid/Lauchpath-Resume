package com.launchpath.resume_service.controller;

import com.launchpath.resume_service.dto.request.CreateTemplateRequestDTO;
import com.launchpath.resume_service.dto.response.ApiResponseDTO;
import com.launchpath.resume_service.dto.response.TemplateResponseDTO;
import com.launchpath.resume_service.entity.Template;
import com.launchpath.resume_service.enums.TemplateCategory;
import com.launchpath.resume_service.exception.RateLimitExceededException;
import com.launchpath.resume_service.mapper.ResumeMapper;
import com.launchpath.resume_service.services.RateLimitService;
import com.launchpath.resume_service.services.TemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;
    private final ResumeMapper resumeMapper;
    private final RateLimitService rateLimitService;

    // ══════════════════════════════════════════════════════════
    // GET ACCESSIBLE TEMPLATES
    // GET /api/v1/templates
    // FREE users → free only, PAID → all
    // ══════════════════════════════════════════════════════════

    @GetMapping
    public ResponseEntity<ApiResponseDTO<List<TemplateResponseDTO>>> getTemplates() {
        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException(
                    "Too many requests. Please wait 1 minute."
            );
        }
        log.debug("Get templates - userId: {}", userId);

        List<TemplateResponseDTO> templates = templateService
                .getAccessibleTemplates(userId)
                .stream()
                .map(resumeMapper::toTemplateDTO)
                .toList();

        return ResponseEntity.ok(
                ApiResponseDTO.success(
                        "Templates fetched successfully",
                        templates
                )
        );
    }

    // ══════════════════════════════════════════════════════════
    // GET BY CATEGORY
    // GET /api/v1/templates/category/{category}
    // ══════════════════════════════════════════════════════════

    @GetMapping("/category/{category}")
    public ResponseEntity<ApiResponseDTO<List<TemplateResponseDTO>>> getByCategory(
            @PathVariable TemplateCategory category){
        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException(
                    "Too many requests. Please wait 1 minute."
            );
        }
        log.debug("Get templates by category: {}", category);

        List<TemplateResponseDTO> templates = templateService
                .getTemplatesByCategory(userId, category)
                .stream()
                .map(resumeMapper::toTemplateDTO)
                .toList();

        return ResponseEntity.ok(
                ApiResponseDTO.success("Templates fetched", templates)
        );
    }

    // ══════════════════════════════════════════════════════════
    // GET SINGLE TEMPLATE
    // GET /api/v1/templates/{id}
    // Checks premium access
    // ══════════════════════════════════════════════════════════

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDTO<TemplateResponseDTO>> getTemplate(
            @PathVariable String id){
        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException(
                    "Too many requests. Please wait 1 minute."
            );
        }
        log.debug("Get template - id: {}", id);

        Template template = templateService.getTemplateById(id, userId);

        return ResponseEntity.ok(
                ApiResponseDTO.success(
                        "Template fetched",
                        resumeMapper.toTemplateDTO(template)
                )
        );
    }

    // ══════════════════════════════════════════════════════════
    // SEARCH TEMPLATES
    // GET /api/v1/templates/search?q=modern
    // ══════════════════════════════════════════════════════════

    @GetMapping("/search")
    public ResponseEntity<ApiResponseDTO<List<TemplateResponseDTO>>> search(
            @RequestParam String q) {

        log.debug("Search templates: {}", q);

        List<TemplateResponseDTO> templates = templateService
                .searchTemplates(q)
                .stream()
                .map(resumeMapper::toTemplateDTO)
                .toList();

        return ResponseEntity.ok(
                ApiResponseDTO.success("Search results", templates)
        );
    }

    // ══════════════════════════════════════════════════════════
    // GET TOP TEMPLATES
    // GET /api/v1/templates/top
    // Sorted by usage count
    // ══════════════════════════════════════════════════════════

    @GetMapping("/top")
    public ResponseEntity<ApiResponseDTO<List<TemplateResponseDTO>>> getTop() {

        List<TemplateResponseDTO> templates = templateService
                .getTopTemplates()
                .stream()
                .map(resumeMapper::toTemplateDTO)
                .toList();

        return ResponseEntity.ok(
                ApiResponseDTO.success("Top templates fetched", templates)
        );
    }

    // ══════════════════════════════════════════════════════════
    // APPLY TEMPLATE TO RESUME
    // POST /api/v1/templates/{id}/apply
    // Increments usage count
    // ══════════════════════════════════════════════════════════

    @PostMapping("/{id}/apply")
    public ResponseEntity<ApiResponseDTO<TemplateResponseDTO>> applyTemplate(
            @PathVariable String id) {
        Long userId = getCurrentUserId();
        if (!rateLimitService.isAllowed(userId)) {
            throw new RateLimitExceededException(
                    "Too many requests. Please wait 1 minute."
            );
        }
        log.info("Apply template - id: {}, userId: {}", id, userId);

        Template applied = templateService.applyTemplate(id, userId);

        return ResponseEntity.ok(
                ApiResponseDTO.success(
                        "Template applied successfully",
                        resumeMapper.toTemplateDTO(applied)
                )
        );
    }

    // ══════════════════════════════════════════════════════════
    // ADMIN — CREATE TEMPLATE
    // POST /api/v1/templates/admin
    // ══════════════════════════════════════════════════════════

    @PostMapping("/admin")
    public ResponseEntity<ApiResponseDTO<TemplateResponseDTO>> createTemplate(
            @Valid @RequestBody CreateTemplateRequestDTO request) {

        log.info("Admin create template: {}", request.getName());

        Template template = Template.builder()
                .name(request.getName())
                .description(request.getDescription())
                .category(request.getCategory())
                .isPremium(request.getIsPremium())
                .primaryColor(request.getPrimaryColor())
                .secondaryColor(request.getSecondaryColor())
                .fontFamily(request.getFontFamily())
                .previewUrl(request.getPreviewUrl())
                .sectionStructure(request.getSectionStructure())
                .build();

        Template created = templateService.createTemplate(template);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponseDTO.success(
                        "Template created successfully",
                        resumeMapper.toTemplateDTO(created)
                ));
    }

    // ══════════════════════════════════════════════════════════
    // ADMIN — DEACTIVATE TEMPLATE
    // DELETE /api/v1/templates/admin/{id}
    // ══════════════════════════════════════════════════════════

    @DeleteMapping("/admin/{id}")
    public ResponseEntity<ApiResponseDTO<Void>> deactivate(
            @PathVariable String id) {

        log.info("Admin deactivate template: {}", id);
        templateService.deactivateTemplate(id);

        return ResponseEntity.ok(
                ApiResponseDTO.success("Template deactivated")
        );
    }
    private Long getCurrentUserId() {
        return (Long) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
    }
}
