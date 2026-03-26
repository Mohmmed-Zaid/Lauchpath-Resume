package com.launchpath.resume_service.feign;

import com.launchpath.resume_service.feign.dto.ApiResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

// "user-service" = spring.application.name of user-service
// Eureka resolves this to actual IP:port automatically
// No hardcoded URL — works even if user-service moves
@FeignClient(name = "user-service")
public interface UserServiceClient {

    // Check user exists before creating resume
    @GetMapping("/api/v1/users/{id}")
    ApiResponseDTO<Object> getUserById(@PathVariable("id") Long userId);

    // Check resume creation limit for user's plan
    @GetMapping("/api/v1/subscriptions/my/can-create")
    ApiResponseDTO<Boolean> canCreateMoreResumes(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam("currentCount") long currentCount
    );

    // Check subscription active before any operation
    @GetMapping("/api/v1/subscriptions/my/active")
    ApiResponseDTO<Boolean> isSubscriptionActive(
            @RequestHeader("X-User-Id") Long userId
    );

    // Consume ATS credit before AI analysis
    @PostMapping("/api/v1/subscriptions/my/consume-ats")
    ApiResponseDTO<Void> consumeAtsCredit(
            @RequestHeader("X-User-Id") Long userId
    );

    // Refund ATS credit if AI call fails
    @PostMapping("/api/v1/subscriptions/my/refund-ats")
    ApiResponseDTO<Void> refundAtsCredit(
            @RequestHeader("X-User-Id") Long userId
    );

    // Consume download credit before PDF/DOCX export
    @PostMapping("/api/v1/subscriptions/my/consume-download")
    ApiResponseDTO<Void> consumeDownloadCredit(
            @RequestHeader("X-User-Id") Long userId
    );

    // Refund download credit if export fails
    @PostMapping("/api/v1/subscriptions/my/refund-download")
    ApiResponseDTO<Void> refundDownloadCredit(
            @RequestHeader("X-User-Id") Long userId
    );

    // Get remaining ATS credits — for UI display
    @GetMapping("/api/v1/subscriptions/my/ats-credits")
    ApiResponseDTO<Integer> getRemainingAtsCredits(
            @RequestHeader("X-User-Id") Long userId
    );

    // Check if user can access premium templates
    @GetMapping("/api/v1/subscriptions/my/active")
    ApiResponseDTO<Boolean> getSubscriptionStatus(
            @RequestHeader("X-User-Id") Long userId
    );
}
