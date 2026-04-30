package com.launchpath.resume_service.feign;

import com.launchpath.resume_service.config.JSearchConfig;
import feign.RequestInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class JSearchFeignConfig {

    private final JSearchConfig jSearchConfig;

    @Bean
    public RequestInterceptor jSearchRequestInterceptor() {
        return requestTemplate -> {
            requestTemplate.header("X-RapidAPI-Key", jSearchConfig.getKey());
            requestTemplate.header("X-RapidAPI-Host", jSearchConfig.getHost());
        };
    }
}
