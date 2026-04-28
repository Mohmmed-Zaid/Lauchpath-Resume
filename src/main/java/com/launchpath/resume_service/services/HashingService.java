package com.launchpath.resume_service.services;

import com.launchpath.resume_service.entity.ResumeSection;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class HashingService {

    /**
     * Generates SHA-256 hash from resume sections content.
     * Used by ai-service to detect if resume changed.
     * Same hash = same resume = ai-service returns cached result.
     * Different hash = resume updated = ai-service runs fresh analysis.
     */
    public String hashSections(List<ResumeSection> sections) {
        if (sections == null || sections.isEmpty()) {
            return DigestUtils.sha256Hex("empty");
        }

        // Build deterministic string from all visible section content
        StringBuilder content = new StringBuilder();
        sections.stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsVisible()))
                .sorted((a, b) -> {
                    if (a.getOrder() == null || b.getOrder() == null)
                        return 0;
                    return a.getOrder().compareTo(b.getOrder());
                })
                .forEach(s -> {
                    content.append(s.getType()).append(":");
                    if (s.getContent() != null) {
                        content.append(s.getContent().toString());
                    }
                    content.append("|");
                });

        String hash = DigestUtils.sha256Hex(content.toString());
        log.debug("Resume hash generated: {}...", hash.substring(0, 8));
        return hash;
    }

    public String hashText(String text) {
        if (text == null || text.isBlank()) {
            return DigestUtils.sha256Hex("empty");
        }
        return DigestUtils.sha256Hex(text.trim().toLowerCase());
    }
}
