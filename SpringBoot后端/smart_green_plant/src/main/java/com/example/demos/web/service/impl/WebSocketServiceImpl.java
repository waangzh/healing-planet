package com.example.demos.web.service.impl;

import com.example.demos.web.service.WebSocketService;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class WebSocketServiceImpl implements WebSocketService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * 向指定用户推送消息
     * @param userId  目标用户ID
     * @param message 消息内容
     */
    public void sendMessageToUser(Long userId, String message) {
        // 发送到用户专属频道（格式：/topic/user/{userId}/messages）
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/messages",
                new WebSocketMessage(message)
        );
    }

    @Data
    @AllArgsConstructor
    public static class WebSocketMessage {
        private String content;
    }
}
