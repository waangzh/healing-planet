package com.green.websocket;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint("/chat/{userId}")
@Component
@Slf4j
public class ChatWebSocketServer {

    private static final ConcurrentHashMap<String, ChatWebSocketServer> WEB_SOCKET_MAP = new ConcurrentHashMap<>();

    private static volatile ChatMessageHandler chatMessageHandler;

    private Session session;

    private String userId;

    @OnOpen
    public void onOpen(Session session, @PathParam("userId") String userId) {
        this.session = session;
        this.userId = userId;

        ChatWebSocketServer old = WEB_SOCKET_MAP.put(userId, this);
        if (old != null && old.session != null && old.session.isOpen()) {
            try {
                old.session.close();
            } catch (IOException e) {
                log.warn("close old chat websocket failed, userId={}", userId, e);
            }
        }

        log.info("chat websocket connected, userId={}, onlineCount={}", userId, WEB_SOCKET_MAP.size());
        try {
            sendMessage("{\"type\":\"system\",\"message\":\"connected\"}");
        } catch (IOException e) {
            log.error("send connect ack failed, userId={}", userId, e);
        }
    }

    @OnClose
    public void onClose() {
        WEB_SOCKET_MAP.remove(userId);
        log.info("chat websocket closed, userId={}, onlineCount={}", userId, WEB_SOCKET_MAP.size());
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        if (StringUtils.isBlank(message)) {
            return;
        }

        try {
            JSONObject payload = JSON.parseObject(message);
            String toUserId = payload.getString("toUserId");
            String content = payload.getString("content");

            if (StringUtils.isBlank(toUserId) || StringUtils.isBlank(content)) {
                sendMessage("{\"type\":\"error\",\"message\":\"toUserId and content are required\"}");
                return;
            }

            if (chatMessageHandler != null) {
                chatMessageHandler.handle(this.userId, toUserId, content);
                return;
            }

            payload.put("type", "private_message");
            payload.put("fromUserId", this.userId);
            sendToUser(toUserId, payload.toJSONString());
        } catch (Exception e) {
            log.error("chat websocket message handle failed, userId={}, payload={}", userId, message, e);
            try {
                sendMessage("{\"type\":\"error\",\"message\":\"invalid payload\"}");
            } catch (IOException ignored) {
            }
        }
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.error("chat websocket error, userId={}", this.userId, error);
    }

    public void sendMessage(String message) throws IOException {
        this.session.getBasicRemote().sendText(message);
    }

    public static void setChatMessageHandler(ChatMessageHandler handler) {
        chatMessageHandler = handler;
    }

    public static boolean sendToUser(String userId, String message) {
        if (StringUtils.isBlank(userId)) {
            return false;
        }
        ChatWebSocketServer target = WEB_SOCKET_MAP.get(userId);
        if (target == null || target.session == null || !target.session.isOpen()) {
            return false;
        }
        try {
            target.session.getBasicRemote().sendText(message);
            return true;
        } catch (IOException e) {
            log.error("chat websocket push failed, toUserId={}", userId, e);
            return false;
        }
    }

    public static boolean isOnline(String userId) {
        ChatWebSocketServer target = WEB_SOCKET_MAP.get(userId);
        return target != null && target.session != null && target.session.isOpen();
    }
}
