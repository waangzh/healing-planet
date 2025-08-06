package com.green.websocket;


import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.OnError;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@ServerEndpoint("/notify/{userId}")
@Component
@Slf4j
public class NotifyWebSocketServer {
    private static final ConcurrentHashMap<String, Session> notifySessions = new ConcurrentHashMap<>();
    // 离线消息队列（ConcurrentHashMap保证线程安全）
    private static final Map<String, Queue<String>> pendingAcks = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("userId") String userId) {
        log.info("用户上线:{}",userId);
        notifySessions.put(userId, session);
        Queue<String> pendingMessages = pendingAcks.get(userId);
        if (pendingMessages != null) {
            while (!pendingMessages.isEmpty()) {
                session.getAsyncRemote().sendText(pendingMessages.poll());
            }
        }
    }

    // 专用于系统通知推送
    public static void sendNotification(String userId, String message) {
        try {
            log.info("推送通知给用户{},消息:{}",userId,message);

            Session session = notifySessions.get(userId);
            if (session != null) {
                if (session.isOpen()) {
                    synchronized (session) {  // 防止并发发送导致消息乱序
                        session.getAsyncRemote().sendText(message);
                    }
                } else {
                    // 会话已关闭，转移到离线队列并清理
                    log.error("用户{}会话已关闭，存储离线消息", userId);
                    pendingAcks.computeIfAbsent(userId, k -> new ConcurrentLinkedQueue<>()).add(message);
                }
            } else {
                // 用户完全离线
                log.error("用户离线{}，存储离线消息", userId);
                pendingAcks.computeIfAbsent(userId, k -> new ConcurrentLinkedQueue<>()).add(message);
            }
        } catch (Exception e) {
            log.error("向用户 {} 发送消息异常: {}，存储离线消息", userId, e.getMessage());
            pendingAcks.computeIfAbsent(userId, k -> new ConcurrentLinkedQueue<>()).add(message);
        }
    }

    @OnError
    public void onError(Session session, @PathParam("userId") String userId, Throwable throwable) {
        log.error("用户 {} 的WebSocket发生错误: {}", userId, throwable.getMessage());
        session = null;
    }


}
