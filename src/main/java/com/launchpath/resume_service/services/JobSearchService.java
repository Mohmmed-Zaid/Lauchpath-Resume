package com.launchpath.resume_service.services;


import com.launchpath.resume_service.dto.request.JobSearchRequestDTO;
import com.launchpath.resume_service.dto.response.JobListingDTO;
import com.launchpath.resume_service.dto.response.JobSearchResponseDTO;
import com.launchpath.resume_service.exception.JobSearchException;
import com.launchpath.resume_service.feign.JSearchFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobSearchService {

    private final JSearchFeignClient jSearchFeignClient;


    public JobSearchResponseDTO searchJobs(JobSearchRequestDTO request) {

        try {
            String query = request.getQuery();
            if (request.getLocation() != null && !request.getLocation().isBlank()) {
                query = query + " in " + request.getLocation();
            }

            Map<String, Object> response = jSearchFeignClient.searchJobs(
                    query,
                    request.getPage(),
                    request.getNumPages(),
                    request.getEmploymentType().name()
            );

            if (response == null || response.get("data") == null) {
                throw new JobSearchException("Empty response from JSearch API");
            }

            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");

            List<JobListingDTO> jobs = data.stream().map(job -> JobListingDTO.builder()
                    .jobId((String) job.get("job_id"))
                    .jobTitle((String) job.get("job_title"))
                    .employerName((String) job.get("employer_name"))
                    .employerLogo((String) job.get("employer_logo"))
                    .jobDescription((String) job.get("job_description"))
                    .jobCity((String) job.get("job_city"))
                    .jobCountry((String) job.get("job_country"))
                    .employmentType((String) job.get("job_employment_type"))
                    .jobApplyLink((String) job.get("job_apply_link"))
                    .jobIsRemote((Boolean) job.get("job_is_remote"))
                    .postedAt((String) job.get("job_posted_at_datetime_utc"))
                    .build()
            ).collect(Collectors.toList());

            log.info("JSearch returned {} jobs for query: {}", jobs.size(), query);

            int currentPage = request.getPage();
            int requestedPages = request.getNumPages();
            boolean hasNext = data.size() >= 10;

            return JobSearchResponseDTO.builder()
                    .status(200)
                    .message("Jobs fetched successfully")
                    .jobs(jobs)
                    .page(currentPage)
                    .numPages(requestedPages)
                    .totalResults(jobs.size())
                    .hasNext(hasNext)
                    .build();

        } catch (JobSearchException e) {
            throw e;
        } catch (Exception e) {
            log.error("JSearch API call failed: {}", e.getMessage());
            throw new JobSearchException("Failed to fetch jobs from JSearch", e);
        }
    }
}