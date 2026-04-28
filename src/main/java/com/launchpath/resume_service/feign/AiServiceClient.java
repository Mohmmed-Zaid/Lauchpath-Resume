package com.launchpath.resume_service.feign;

import com.launchpath.resume_service.feign.dto.AiAnalyzeRequestDTO;
import com.launchpath.resume_service.feign.dto.AiAnalyzeResponseDTO;
import com.launchpath.resume_service.feign.dto.ApiResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "ai-service",
        fallback = AiServiceClientFallback.class
)
public interface AiServiceClient {

    @PostMapping("/api/v1/ai/analyze-career")
    ApiResponseDTO<AiAnalyzeResponseDTO> analyzeCareer(
            @RequestBody AiAnalyzeRequestDTO request
    );
}
