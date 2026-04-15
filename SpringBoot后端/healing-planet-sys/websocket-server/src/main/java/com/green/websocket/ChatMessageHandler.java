package com.green.websocket;

public interface ChatMessageHandler {
    void handle(String fromUserId, String toUserId, String content);
}
