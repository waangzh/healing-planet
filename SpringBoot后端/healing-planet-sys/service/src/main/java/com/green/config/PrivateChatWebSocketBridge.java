package com.green.config;

import com.green.service.IPrivateMessageService;
import com.green.websocket.ChatWebSocketServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

@Configuration
public class PrivateChatWebSocketBridge {

    @Autowired
    private IPrivateMessageService privateMessageService;

    @PostConstruct
    public void bindHandler() {
        ChatWebSocketServer.setChatMessageHandler(privateMessageService::handleWebSocketSend);
    }
}
