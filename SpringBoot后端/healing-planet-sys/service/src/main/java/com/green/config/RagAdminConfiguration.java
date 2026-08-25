package com.green.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RagAdminConfiguration {
    @Bean("ragAdminRestTemplate")
    public RestTemplate ragAdminRestTemplate(RestTemplateBuilder builder, RagAdminProperties properties) {
        return builder.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMillis())).build();
    }
}
