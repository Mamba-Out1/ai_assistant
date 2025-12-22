package com.medical.assistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "dify")
public class DifyConfig {
    private String apiUrl = "https://api.dify.ai/v1";
    private String apiKey = "app-cryaKwKH7agSYl9PxKcDRPwb";
}
