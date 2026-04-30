package com.launchpath.resume_service.controller;


import com.launchpath.resume_service.dto.request.JobSearchRequestDTO;
import com.launchpath.resume_service.dto.response.JobSearchResponseDTO;
import com.launchpath.resume_service.enums.EmploymentType;
import com.launchpath.resume_service.services.JobSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobSearchService jobSearchService;

    @GetMapping("/search")
    public ResponseEntity<JobSearchResponseDTO> searchJobs(
            @RequestParam String query,
            @RequestParam(defaultValue = "") String location,
            @RequestParam(defaultValue = "FULLTIME") EmploymentType employmentType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "1") int numPages
    ) {
        JobSearchRequestDTO request = new JobSearchRequestDTO();
        request.setQuery(query);
        request.setLocation(location);
        request.setEmploymentType(employmentType);
        request.setPage(page);
        request.setNumPages(numPages);

        return ResponseEntity.ok(jobSearchService.searchJobs(request));
    }
}