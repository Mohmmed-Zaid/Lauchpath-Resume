package com.launchpath.resume_service.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class RateLimitService {

    @Value("${rate.limit.requests-per-minute:20}")
    private int requestsPerMinute;

    private final Map<Long, AtomicInteger> requestCounts =
            new ConcurrentHashMap<>();

    public boolean isAllowed(Long userId) {
        AtomicInteger count = requestCounts.computeIfAbsent(
                userId, k -> new AtomicInteger(0)
        );
        int current = count.incrementAndGet();

        if (current > requestsPerMinute) {
            log.warn("Rate limit exceeded for userId: {}", userId);
            return false;
        }
        return true;
    }

    @Scheduled(fixedRate = 60000)
    public void resetCounts() {
        requestCounts.clear();
        log.debug("Rate limit counts reset");
    }
}