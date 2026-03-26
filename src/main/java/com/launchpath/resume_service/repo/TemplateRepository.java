package com.launchpath.resume_service.repo;

import com.launchpath.resume_service.entity.Template;
import com.launchpath.resume_service.enums.TemplateCategory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TemplateRepository extends MongoRepository<Template, String> {

    // ── Basic Finders ─────────────────────────────────────────

    // All active templates — shown in template gallery
    List<Template> findByIsActiveTrue();

    // Free templates only — shown to free tier users
    List<Template> findByIsPremiumFalseAndIsActiveTrue();

    // All active templates regardless of premium status
    // Shown to paid users
    List<Template> findByIsActiveTrueOrderByUsageCountDesc();

    // By category — filter in template gallery
    List<Template> findByCategoryAndIsActiveTrue(TemplateCategory category);

    // Premium templates by category
    List<Template> findByCategoryAndIsPremiumAndIsActiveTrue(
            TemplateCategory category, Boolean isPremium
    );

    // Existence check
    boolean existsByName(String name);

    Optional<Template> findByName(String name);

    // ── MongoDB @Query ────────────────────────────────────────

    // Search templates by name — for template search bar
    @Query("{ 'name': { $regex: ?0, $options: 'i' }, 'isActive': true }")
    List<Template> searchByName(String name);

    // Top N most used templates
    @Query(value = "{ 'isActive': true }",
            sort = "{ 'usageCount': -1 }")
    List<Template> findTopTemplates();

    // Free templates sorted by popularity
    @Query(value = "{ 'isPremium': false, 'isActive': true }",
            sort = "{ 'usageCount': -1 }")
    List<Template> findFreeTemplatesSortedByUsage();
}