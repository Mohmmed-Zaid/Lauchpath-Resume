package com.launchpath.resume_service.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OptimizeResumeRequestDTO {

    // if provided AI optimizes for this role
    private String targetJobTitle;

    // if provided AI optimizes for this JD
    private String jobDescription;
}
