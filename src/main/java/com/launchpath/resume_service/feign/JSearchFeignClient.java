package com.launchpath.resume_service.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(
        name = "jsearch-client",
        url = "${jsearch.api.url}",
        configuration = JSearchFeignConfig.class
)
public interface JSearchFeignClient {

    @GetMapping
    Map<String, Object> searchJobs(
            @RequestParam("query") String query,
            @RequestParam("page") int page,
            @RequestParam("num_pages") int numPages,
            @RequestParam("employment_types") String employmentType
    );
}
