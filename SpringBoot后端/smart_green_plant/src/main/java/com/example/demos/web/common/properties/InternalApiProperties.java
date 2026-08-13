package com.example.demos.web.common.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "plant.internal-api")
@Data
public class InternalApiProperties {
    private String key = "";
}
