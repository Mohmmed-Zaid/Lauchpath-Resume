package com.launchpath.resume_service.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class JobSearchResponseDTO {
    private int status;
    private String message;
    private List<JobListingDTO> jobs;

    private int page;
    private int numPages;
    private int totalResults;
    private boolean hasNext;
}