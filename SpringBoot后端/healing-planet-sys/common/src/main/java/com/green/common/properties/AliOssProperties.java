package com.green.common.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "smart.alioss")
@Data
public class AliOssProperties {

    //读取配置文件的属性，封装成对应的java对象
    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String bucketName;

}
