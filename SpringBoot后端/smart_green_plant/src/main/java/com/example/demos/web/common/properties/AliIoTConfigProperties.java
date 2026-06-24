package com.example.demos.web.common.properties;

import lombok.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 */

@Data
@NoArgsConstructor
@ToString
@Configuration
@ConfigurationProperties(prefix = "iot.aliyun")
public class AliIoTConfigProperties {

    /**
     * 访问Key
     */
    private String accessKeyId;
    /**
     * 访问秘钥
     */
    private String accessKeySecret;
    /**
     * 区域id
     */
    private String regionId;
    /**
     * 实例id
     */
    private String iotInstanceId;
    /**
     * 域名
     */
    private String host;

    /**
     * 消费组
     */
    private String consumerGroupId;

    /**
     * 产品密钥
     */
    private String productKey;

}