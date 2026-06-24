package com.example.demos.web.config;

import com.example.demos.web.common.properties.AliOssProperties;
import com.example.demos.web.utils.AliOssUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*
*配置类，用于创建AliOssUtil对象
* */
@Configuration
@Slf4j
public class OssConfiguration {

    @Bean//添加这个注解之后，当项目启动的时候，就会调用这个方法，将这个对象创建处理，交给spring容器管理
    @ConditionalOnMissingBean//保证整个容器里面只有这一个Util对象
    public AliOssUtil aliOssUtil(AliOssProperties aliOssProperties){
        log.info("开始创建阿里云文件上传工具类对象：{}",aliOssProperties);
        return new AliOssUtil(aliOssProperties.getEndpoint(),
                                aliOssProperties.getAccessKeyId(),
                                aliOssProperties.getAccessKeySecret(),
                                aliOssProperties.getBucketName());
    }
}
