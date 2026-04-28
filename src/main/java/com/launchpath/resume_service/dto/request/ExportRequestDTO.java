package com.launchpath.resume_service.dto.request;

import com.launchpath.resume_service.enums.ExportFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExportRequestDTO {

    @NotNull(message = "Export format is required")
    private ExportFormat format; // PDF or DOCX
}
