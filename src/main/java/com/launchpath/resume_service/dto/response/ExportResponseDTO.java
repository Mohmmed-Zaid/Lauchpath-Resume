package com.launchpath.resume_service.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExportResponseDTO {

    private String url;           // Cloudinary download URL
    private String format;        // PDF or DOCX
    private String cloudinaryId;  // for tracking
    private String resumeTitle;
}
