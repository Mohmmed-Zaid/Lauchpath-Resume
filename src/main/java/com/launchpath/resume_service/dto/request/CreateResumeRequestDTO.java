package com.launchpath.resume_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateResumeRequestDTO {

    @NotBlank(message = "Title is required")
    @Size(min = 2, max = 100, message = "Title must be between 2 and 100 characters")
    private String title;

    // Optional — template applied later if not provided
    private String templateId;

    // Optional — used by AI for tailoring
    private String targetJobTitle;

    // Optional
    private String targetCompany;
}