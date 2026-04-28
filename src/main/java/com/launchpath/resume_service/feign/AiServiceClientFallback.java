package com.launchpath.resume_service.feign;

import com.launchpath.resume_service.feign.dto.AiAnalyzeRequestDTO;
import com.launchpath.resume_service.feign.dto.AiAnalyzeResponseDTO;
import com.launchpath.resume_service.feign.dto.ApiResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class AiServiceClientFallback implements AiServiceClient {

    @Override
    public ApiResponseDTO<AiAnalyzeResponseDTO> analyzeCareer(
            AiAnalyzeRequestDTO request) {

        log.warn("ai-service unavailable — returning fallback for userId: {}",
                request.getUserId());

        // Return empty result — resume creation still succeeds
        // User can trigger analysis manually later
        AiAnalyzeResponseDTO fallback = AiAnalyzeResponseDTO.builder()
                .readinessScore(0)
                .readinessLabel("UNAVAILABLE")
                .fromCache(false)
                .errorMessage("AI service temporarily unavailable. " +
                        "Please run analysis manually.")
                .build();

        return ApiResponseDTO.<AiAnalyzeResponseDTO>builder()
                .success(false)
                .message("AI service unavailable")
                .data(fallback)
                .build();
    }
}