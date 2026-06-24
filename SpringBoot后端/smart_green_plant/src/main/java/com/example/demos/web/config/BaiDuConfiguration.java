package com.example.demos.web.config;

import com.example.demos.web.common.properties.BaiDuProperties;
import com.example.demos.web.utils.BaiDuUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class BaiDuConfiguration {
    @Bean
    @ConditionalOnMissingBean//如果没有这个bean才创建，保证只有一个
    public BaiDuUtil baiDuUtil(BaiDuProperties baiDuProperties){
        log.info("开始创建智能问答ai工具类对象：{}",baiDuProperties);
        return new BaiDuUtil(baiDuProperties.getApiKey(),
                baiDuProperties.getSecretKey(),baiDuProperties.getMultimodalApiKey());
    }
}
