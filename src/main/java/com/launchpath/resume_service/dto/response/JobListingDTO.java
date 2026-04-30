package com.launchpath.resume_service.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class JobListingDTO {
    private String jobId;
    private String jobTitle;
    private String employerName;
    private String employerLogo;
    private String jobDescription;
    private String jobCity;
    private String jobCountry;
    private String employmentType;
    private String jobApplyLink;
    private Boolean jobIsRemote;
    private String postedAt;
}

