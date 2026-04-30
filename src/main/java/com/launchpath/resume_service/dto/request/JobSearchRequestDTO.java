package com.launchpath.resume_service.dto.request;

import com.launchpath.resume_service.enums.EmploymentType;
import lombok.Data;

@Data
public class JobSearchRequestDTO {
    private String query;
    private String location;
    private EmploymentType employmentType = EmploymentType.FULLTIME;
    private int page = 1;
    private int numPages = 1;
}