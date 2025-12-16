package com.medical.assistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "xunfei")
public class XunfeiConfig {
    private String appId;
    private String apiKey;
    private String apiSecret;
    private String websocketUrl;

    private Audio audio = new Audio();

    @Data
    public static class Audio {
        private String encode;
        private Integer sampleRate;
        private String lang;
    }
}