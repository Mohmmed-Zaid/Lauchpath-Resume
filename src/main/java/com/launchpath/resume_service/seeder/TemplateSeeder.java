package com.launchpath.resume_service.seeder;


import com.launchpath.resume_service.entity.Template;
import com.launchpath.resume_service.enums.TemplateCategory;
import com.launchpath.resume_service.repo.TemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TemplateSeeder implements CommandLineRunner {

    private final TemplateRepository templateRepository;

    @Override
    public void run(String... args) {
        if (templateRepository.count() > 0) return;

        List<Template> templates = List.of(

                // ── FREE TEMPLATES (5) ────────────────────────────

                Template.builder()
                        .name("Classic Professional")
                        .description("Clean, traditional layout. ATS-friendly.")
                        .category(TemplateCategory.PROFESSIONAL)
                        .isPremium(false)
                        .primaryColor("#2C3E50")
                        .secondaryColor("#ECF0F1")
                        .fontFamily("Arial")
                        .usageCount(0)
                        .isActive(true)
                        .sectionStructure(List.of(
                                Map.of("type", "PERSONAL_INFO", "order", 0),
                                Map.of("type", "SUMMARY",       "order", 1),
                                Map.of("type", "EXPERIENCE",    "order", 2),
                                Map.of("type", "EDUCATION",     "order", 3),
                                Map.of("type", "SKILLS",        "order", 4)
                        ))
                        .build(),

                Template.builder()
                        .name("Minimal Clean")
                        .description("Minimal whitespace design. Modern and elegant.")
                        .category(TemplateCategory.MINIMAL)
                        .isPremium(false)
                        .primaryColor("#333333")
                        .secondaryColor("#FFFFFF")
                        .fontFamily("Helvetica")
                        .usageCount(0)
                        .isActive(true)
                        .sectionStructure(List.of(
                                Map.of("type", "PERSONAL_INFO", "order", 0),
                                Map.of("type", "EXPERIENCE",    "order", 1),
                                Map.of("type", "EDUCATION",     "order", 2),
                                Map.of("type", "SKILLS",        "order", 3)
                        ))
                        .build(),

                Template.builder()
                        .name("Modern Bold")
                        .description("Bold headers, strong visual hierarchy.")
                        .category(TemplateCategory.MODERN)
                        .isPremium(false)
                        .primaryColor("#E74C3C")
                        .secondaryColor("#F8F9FA")
                        .fontFamily("Georgia")
                        .usageCount(0)
                        .isActive(true)
                        .sectionStructure(List.of(
                                Map.of("type", "PERSONAL_INFO", "order", 0),
                                Map.of("type", "SUMMARY",       "order", 1),
                                Map.of("type", "EXPERIENCE",    "order", 2),
                                Map.of("type", "SKILLS",        "order", 3),
                                Map.of("type", "EDUCATION",     "order", 4),
                                Map.of("type", "PROJECTS",      "order", 5)
                        ))
                        .build(),

                Template.builder()
                        .name("Academic Standard")
                        .description("CV format for academic and research roles.")
                        .category(TemplateCategory.ACADEMIC)
                        .isPremium(false)
                        .primaryColor("#1A237E")
                        .secondaryColor("#E8EAF6")
                        .fontFamily("Times New Roman")
                        .usageCount(0)
                        .isActive(true)
                        .sectionStructure(List.of(
                                Map.of("type", "PERSONAL_INFO",   "order", 0),
                                Map.of("type", "SUMMARY",         "order", 1),
                                Map.of("type", "EDUCATION",       "order", 2),
                                Map.of("type", "EXPERIENCE",      "order", 3),
                                Map.of("type", "PUBLICATIONS",    "order", 4),
                                Map.of("type", "CERTIFICATIONS",  "order", 5)
                        ))
                        .build(),

                Template.builder()
                        .name("Creative Portfolio")
                        .description("Stand out with a creative sidebar layout.")
                        .category(TemplateCategory.CREATIVE)
                        .isPremium(false)
                        .primaryColor("#6C3483")
                        .secondaryColor("#F5EEF8")
                        .fontFamily("Calibri")
                        .usageCount(0)
                        .isActive(true)
                        .sectionStructure(List.of(
                                Map.of("type", "PERSONAL_INFO", "order", 0),
                                Map.of("type", "SKILLS",        "order", 1),
                                Map.of("type", "EXPERIENCE",    "order", 2),
                                Map.of("type", "PROJECTS",      "order", 3),
                                Map.of("type", "EDUCATION",     "order", 4)
                        ))
                        .build(),

                // ── PREMIUM TEMPLATES (5) ─────────────────────────

                Template.builder()
                        .name("Executive Elite")
                        .description("Premium executive look. For senior roles.")
                        .category(TemplateCategory.PROFESSIONAL)
                        .isPremium(true)
                        .primaryColor("#1B2631")
                        .secondaryColor("#D4AC0D")
                        .fontFamily("Garamond")
                        .usageCount(0)
                        .isActive(true)
                        .sectionStructure(List.of(
                                Map.of("type", "PERSONAL_INFO", "order", 0),
                                Map.of("type", "SUMMARY",       "order", 1),
                                Map.of("type", "EXPERIENCE",    "order", 2),
                                Map.of("type", "EDUCATION",     "order", 3),
                                Map.of("type", "SKILLS",        "order", 4),
                                Map.of("type", "AWARDS",        "order", 5)
                        ))
                        .build(),

                Template.builder()
                        .name("Tech Innovator")
                        .description("Built for software engineers and developers.")
                        .category(TemplateCategory.MODERN)
                        .isPremium(true)
                        .primaryColor("#0D47A1")
                        .secondaryColor("#E3F2FD")
                        .fontFamily("Roboto")
                        .usageCount(0)
                        .isActive(true)
                        .sectionStructure(List.of(
                                Map.of("type", "PERSONAL_INFO",  "order", 0),
                                Map.of("type", "SUMMARY",        "order", 1),
                                Map.of("type", "SKILLS",         "order", 2),
                                Map.of("type", "EXPERIENCE",     "order", 3),
                                Map.of("type", "PROJECTS",       "order", 4),
                                Map.of("type", "EDUCATION",      "order", 5),
                                Map.of("type", "CERTIFICATIONS", "order", 6)
                        ))
                        .build(),

                Template.builder()
                        .name("Creative Director")
                        .description("Bold creative layout for design professionals.")
                        .category(TemplateCategory.CREATIVE)
                        .isPremium(true)
                        .primaryColor("#E91E63")
                        .secondaryColor("#FCE4EC")
                        .fontFamily("Futura")
                        .usageCount(0)
                        .isActive(true)
                        .sectionStructure(List.of(
                                Map.of("type", "PERSONAL_INFO", "order", 0),
                                Map.of("type", "SUMMARY",       "order", 1),
                                Map.of("type", "EXPERIENCE",    "order", 2),
                                Map.of("type", "PROJECTS",      "order", 3),
                                Map.of("type", "SKILLS",        "order", 4)
                        ))
                        .build(),

                Template.builder()
                        .name("Finance Professional")
                        .description("Conservative design for banking and finance.")
                        .category(TemplateCategory.PROFESSIONAL)
                        .isPremium(true)
                        .primaryColor("#1A5276")
                        .secondaryColor("#EBF5FB")
                        .fontFamily("Cambria")
                        .usageCount(0)
                        .isActive(true)
                        .sectionStructure(List.of(
                                Map.of("type", "PERSONAL_INFO", "order", 0),
                                Map.of("type", "SUMMARY",       "order", 1),
                                Map.of("type", "EXPERIENCE",    "order", 2),
                                Map.of("type", "EDUCATION",     "order", 3),
                                Map.of("type", "CERTIFICATIONS","order", 4),
                                Map.of("type", "SKILLS",        "order", 5)
                        ))
                        .build(),

                Template.builder()
                        .name("Startup Founder")
                        .description("Dynamic layout for entrepreneurs and founders.")
                        .category(TemplateCategory.MODERN)
                        .isPremium(true)
                        .primaryColor("#FF6F00")
                        .secondaryColor("#FFF8E1")
                        .fontFamily("Open Sans")
                        .usageCount(0)
                        .isActive(true)
                        .sectionStructure(List.of(
                                Map.of("type", "PERSONAL_INFO", "order", 0),
                                Map.of("type", "SUMMARY",       "order", 1),
                                Map.of("type", "EXPERIENCE",    "order", 2),
                                Map.of("type", "PROJECTS",      "order", 3),
                                Map.of("type", "SKILLS",        "order", 4),
                                Map.of("type", "AWARDS",        "order", 5),
                                Map.of("type", "EDUCATION",     "order", 6)
                        ))
                        .build()
        );

        templateRepository.saveAll(templates);
        log.info("Templates seeded successfully — {} templates", templates.size());
    }
}
