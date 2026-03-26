package com.launchpath.resume_service.entity;

import com.launchpath.resume_service.entity.base.BaseDocument;
import com.launchpath.resume_service.enums.ResumeStatus;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

// @Document maps to MongoDB collection "resumes"
@Document(collection = "resumes")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Resume extends BaseDocument {

    @Id
    private String id; // MongoDB ObjectId as String

    // FK to MySQL users table — String because MongoDB
    // @Indexed — frequently queried, speeds up findByUserId
    @Indexed
    private String userId;

    private String title; // "Software Engineer Resume", "Marketing CV"

    private ResumeStatus status;

    // Which template is applied
    private String templateId;

    // Current version number — increments on every save
    private Integer currentVersion;

    // Target job title — used by AI for tailoring
    private String targetJobTitle;

    // Target company — used by AI for company-specific tailoring
    private String targetCompany;

    // Active sections in current state
    @Builder.Default
    private List<ResumeSection> sections = new ArrayList<>();

    // Full version history — each save appended here
    // Capped at 50 versions to control document size
    @Builder.Default
    private List<ResumeVersion> versions = new ArrayList<>();

    // All ATS analyses ever run on this resume
    @Builder.Default
    private List<AtsResult> atsResults = new ArrayList<>();

    // Cloudinary file record IDs of exported files
    // key = "PDF_v3", value = cloudinary public_id
    @Builder.Default
    private List<String> exportedFileIds = new ArrayList<>();

    // Is this resume currently being edited (WebSocket session active)
    private Boolean isLocked;

    // Who has the edit lock (userId)
    private String lockedBy;
}
