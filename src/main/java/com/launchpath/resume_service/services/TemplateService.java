package com.launchpath.resume_service.services;


import com.launchpath.resume_service.entity.Template;
import com.launchpath.resume_service.enums.TemplateCategory;
import com.launchpath.resume_service.exception.ResourceNotFoundException;
import com.launchpath.resume_service.exception.UnauthorizedAccessException;
import com.launchpath.resume_service.feign.UserServiceClient;
import com.launchpath.resume_service.repo.ResumeRepository;
import com.launchpath.resume_service.repo.TemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateService {

    private final TemplateRepository templateRepository;
    private final ResumeRepository resumeRepository;
    private final UserServiceClient userServiceClient;

    // ══════════════════════════════════════════════════════════
    // READ
    // ══════════════════════════════════════════════════════════

    /**
     * Returns templates based on user's subscription.
     * FREE users → free templates only
     * PAID users → all templates
     */
    public List<Template> getAccessibleTemplates(Long userId) {
        log.debug("Fetching templates for userId: {}", userId);

        Boolean isActive = userServiceClient
                .isSubscriptionActive(userId)
                .getData();

        // Check if user has paid plan
        // For now using subscription active check
        // Phase 3: add isPaidPlan check via Feign
        // FREE plan users get free templates only
        boolean isPaidUser = Boolean.TRUE.equals(isActive);

        if (isPaidUser) {
            return templateRepository.findByIsActiveTrueOrderByUsageCountDesc();
        } else {
            return templateRepository.findByIsPremiumFalseAndIsActiveTrue();
        }
    }

    public List<Template> getAllTemplates() {
        return templateRepository.findByIsActiveTrueOrderByUsageCountDesc();
    }

    public List<Template> getFreeTemplates() {
        return templateRepository.findFreeTemplatesSortedByUsage();
    }

    public List<Template> getTemplatesByCategory(Long userId,
                                                 TemplateCategory category) {
        log.debug("Fetching templates by category: {}", category);

        Boolean isActive = userServiceClient
                .isSubscriptionActive(userId)
                .getData();

        boolean isPaidUser = Boolean.TRUE.equals(isActive);

        if (isPaidUser) {
            return templateRepository.findByCategoryAndIsActiveTrue(category);
        } else {
            return templateRepository
                    .findByCategoryAndIsPremiumAndIsActiveTrue(category, false);
        }
    }

    public Template getTemplateById(String templateId, Long userId) {
        log.debug("Fetching template: {}", templateId);

        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Template not found: " + templateId
                ));

        // Premium template access check
        if (Boolean.TRUE.equals(template.getIsPremium())) {
            Boolean isActive = userServiceClient
                    .isSubscriptionActive(userId)
                    .getData();

            if (!Boolean.TRUE.equals(isActive)) {
                throw new UnauthorizedAccessException(
                        "Premium template requires an active paid subscription"
                );
            }
        }

        return template;
    }

    public List<Template> searchTemplates(String query) {
        return templateRepository.searchByName(query);
    }

    public List<Template> getTopTemplates() {
        return templateRepository.findTopTemplates();
    }

    // ══════════════════════════════════════════════════════════
    // APPLY TEMPLATE TO RESUME
    // ══════════════════════════════════════════════════════════

    /**
     * Applies a template to a resume.
     * Checks premium access, increments usage count.
     */
    public Template applyTemplate(String templateId, Long userId) {
        log.info("Applying template: {} for userId: {}", templateId, userId);

        Template template = getTemplateById(templateId, userId);

        // Increment usage count
        template.setUsageCount(template.getUsageCount() + 1);
        templateRepository.save(template);

        log.info("Template applied: {}", templateId);
        return template;
    }

    // ══════════════════════════════════════════════════════════
    // ADMIN — CREATE / UPDATE / DELETE
    // ══════════════════════════════════════════════════════════

    public Template createTemplate(Template template) {
        log.info("Creating template: {}", template.getName());

        if (templateRepository.existsByName(template.getName())) {
            throw new IllegalArgumentException(
                    "Template already exists: " + template.getName()
            );
        }

        template.setUsageCount(0);
        template.setIsActive(true);
        Template saved = templateRepository.save(template);
        log.info("Template created: {}", saved.getId());
        return saved;
    }

    public Template updateTemplate(String templateId, Template updated) {
        log.info("Updating template: {}", templateId);

        Template existing = templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Template not found: " + templateId
                ));

        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setCategory(updated.getCategory());
        existing.setIsPremium(updated.getIsPremium());
        existing.setPrimaryColor(updated.getPrimaryColor());
        existing.setSecondaryColor(updated.getSecondaryColor());
        existing.setFontFamily(updated.getFontFamily());
        existing.setSectionStructure(updated.getSectionStructure());

        return templateRepository.save(existing);
    }

    public void deactivateTemplate(String templateId) {
        log.info("Deactivating template: {}", templateId);

        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Template not found: " + templateId
                ));

        template.setIsActive(false);
        templateRepository.save(template);
    }
}