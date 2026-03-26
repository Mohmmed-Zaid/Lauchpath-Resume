package com.launchpath.resume_service.entity;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeVersion {

    // Version number — increments on every save
    private Integer versionNumber;

    // Who triggered this save
    private String savedBy; // "USER" or "AUTO_SAVE"

    private LocalDateTime savedAt;

    // Optional label — user can name versions
    // e.g. "Google Application", "Final v2"
    private String label;

    // Full snapshot of all sections at this version
    // Deep copy — changes to current don't affect history
    private List<ResumeSection> sections;
}
