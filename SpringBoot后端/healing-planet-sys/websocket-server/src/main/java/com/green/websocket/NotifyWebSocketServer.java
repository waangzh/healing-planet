package com.green.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;

@ServerEndpoint("/notify/{userId}")
@Component
@Slf4j
public class NotifyWebSocketServer {
    // 存储在线用户会话（userId -> Session）
    private static final ConcurrentHashMap<String, Session> notifySessions = new ConcurrentHashMap<>();
    // 离线消息队列
    private static final Map<String, Queue<String>> pendingAcks = new ConcurrentHashMap<>();
    // 心跳调度器（定时检测连接）
    private static final ScheduledExecutorService heartBeatExecutor = Executors.newScheduledThreadPool(1);

    static {
        // 初始化心跳检测（每30秒执行一次）
        heartBeatExecutor.scheduleAtFixedRate(() -> {
            for (Map.Entry<String, Session> entry : notifySessions.entrySet()) {
                String userId = entry.getKey();
                Session session = entry.getValue();
                if (session == null || !session.isOpen()) {
                    notifySessions.remove(userId);
                    log.info("心跳检测：用户{}会话已关闭，移除在线列表", userId);
                    continue;
                }
                try {
                    // 发送ping帧（浏览器会自动回复pong）
                    session.getBasicRemote().sendPing(null);
                    log.debug("向用户{}发送心跳ping", userId);
                } catch (IOException e) {
                    log.error("用户{}心跳发送失败，强制下线", userId, e);
                    notifySessions.remove(userId);
                    try {
                        session.close(new CloseReason(CloseReason.CloseCodes.UNEXPECTED_CONDITION, "心跳失败"));
                    } catch (IOException ex) {
                        // 忽略关闭异常
                    }
                }
            }
        }, 30, 30, TimeUnit.SECONDS); // 延迟30秒开始，每30秒一次
    }

    @OnOpen
    public void onOpen(Session session, @PathParam("userId") String userId) {
        log.info("用户上线:{}，会话ID:{}", userId, session.getId());
        // 防止同一用户多端登录覆盖（根据业务需求调整，这里保留最新连接）
        notifySessions.put(userId, session);

        // 发送离线消息
        Queue<String> pendingMessages = pendingAcks.get(userId);
        if (pendingMessages != null) {
            while (!pendingMessages.isEmpty()) {
                String message = pendingMessages.poll();
                sendAsyncMessage(session, userId, message); // 使用带回调的异步发送
            }
        }
    }

    @OnClose
    public void onClose(Session session, @PathParam("userId") String userId, CloseReason reason) {
        // 连接关闭时移除会话
        notifySessions.remove(userId);
        log.info("用户下线:{}，原因:{}，会话ID:{}", userId, reason.getReasonPhrase(), session.getId());
    }

    @OnError
    public void onError(Session session, @PathParam("userId") String userId, Throwable throwable) {
        log.error("用户 {} 的WebSocket发生错误: {}", userId, throwable.getMessage(), throwable);
        // 错误发生时主动清理会话
        if (userId != null) {
            notifySessions.remove(userId);
        }
        try {
            if (session != null && session.isOpen()) {
                session.close(new CloseReason(CloseReason.CloseCodes.UNEXPECTED_CONDITION, "发生错误"));
            }
        } catch (IOException e) {
            log.error("关闭异常会话失败", e);
        }
    }

    // 处理客户端消息（可用于接收客户端心跳或其他指令）
    @OnMessage
    public void onMessage(String message, @PathParam("userId") String userId) {
        log.info("收到用户{}的消息:{}", userId, message);
        // 如果是心跳回复，无需处理；如果是业务消息，这里添加逻辑
    }

    // 系统通知推送（带异步发送回调）
    public static void sendNotification(String userId, String message) {
        try {
            Session session = notifySessions.get(userId);
            if (session != null && session.isOpen()) {
                sendAsyncMessage(session, userId, message);
            } else {
                // 会话无效，存入离线队列
                log.info("用户{}离线，存储离线消息", userId);
                pendingAcks.computeIfAbsent(userId, k -> new ConcurrentLinkedQueue<>()).add(message);
            }
        } catch (Exception e) {
            log.error("向用户 {} 发送消息异常，存储离线消息", userId, e);
            pendingAcks.computeIfAbsent(userId, k -> new ConcurrentLinkedQueue<>()).add(message);
        }
    }

    private static void sendAsyncMessage(Session session, String userId, String message) {
        // 获取异步发送的Future对象
        Future<Void> future = session.getAsyncRemote().sendText(message);

        // 使用线程监听异步结果（避免阻塞主线程）
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                // 等待发送完成（会阻塞当前子线程，但不影响主线程）
                future.get(); // 阻塞直到发送完成或抛出异常
                log.info("用户{}消息发送成功", userId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // 恢复中断状态
                log.error("用户{}消息发送被中断", userId, e);
                handleSendFailure(userId, message); // 处理发送失败
            } catch (ExecutionException e) {
                log.error("用户{}消息发送失败（异步）", userId, e.getCause());
                handleSendFailure(userId, message); // 处理发送失败
            }
        });
    }

    // 抽取发送失败处理逻辑
    private static void handleSendFailure(String userId, String message) {
        // 发送失败时存入离线队列
        pendingAcks.computeIfAbsent(userId, k -> new ConcurrentLinkedQueue<>()).add(message);
        // 移除无效会话
        notifySessions.remove(userId);
    }
}
