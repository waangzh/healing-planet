package com.green.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * 开启WebSocket支持
 */
@Configuration
public class WebSocketConfig {

    @Bean // 注入ServerEndpointExporter，自动注册使用@ServerEndpoint
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }

}
