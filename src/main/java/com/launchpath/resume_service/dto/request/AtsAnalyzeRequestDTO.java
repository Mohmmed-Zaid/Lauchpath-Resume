package com.launchpath.resume_service.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AtsAnalyzeRequestDTO {

    // Optional — if provided, AI tailors analysis to job
    private String jobDescription;
}
