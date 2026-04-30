package com.launchpath.resume_service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "jsearch.api")
@Data
public class JSearchConfig {
    private String url;
    private String host;
    private String key;
}
