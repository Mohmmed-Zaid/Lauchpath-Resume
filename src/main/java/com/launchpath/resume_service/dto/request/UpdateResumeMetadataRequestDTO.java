package com.launchpath.resume_service.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateResumeMetadataRequestDTO {

    @Size(min = 2, max = 100, message = "Title must be between 2 and 100 characters")
    private String title;

    private String targetJobTitle;
    private String targetCompany;
    private String templateId;
}