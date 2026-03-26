package com.launchpath.resume_service.entity;
//Temporary document — stores parsed text from uploaded file
// Deleted after user confirms sections
import com.launchpath.resume_service.entity.base.BaseDocument;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

@Document(collection = "parsed_resumes")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedResume extends BaseDocument {

    @Id
    private String id;

    private String userId;

    // Cloudinary ID of uploaded file
    private String cloudinaryFileId;

    // Original filename
    private String originalFileName;

    // Raw extracted text from Tika
    private String rawText;

    // AI-parsed structured sections
    // Map of sectionType → extracted content
    private Map<String, Object> parsedSections;

    // Was AI parsing successful
    private Boolean isParsed;

    // Parsing error if failed
    private String parseError;

    // TTL — auto-deleted after 24 hours
    // Set via MongoDB TTL index in config
    private java.time.LocalDateTime expiresAt;
}
