package com.launchpath.resume_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RewriteSectionRequestDTO {

    @NotBlank(message = "Job description is required")
    private String jobDescription;

    private String sectionId;
}
