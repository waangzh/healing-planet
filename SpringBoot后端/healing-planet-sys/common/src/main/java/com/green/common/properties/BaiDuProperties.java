package com.green.common.properties;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "baidu")
@Data
public class BaiDuProperties {
    private String apiKey;
    private String secretKey;
    private String multimodalApiKey;
}
