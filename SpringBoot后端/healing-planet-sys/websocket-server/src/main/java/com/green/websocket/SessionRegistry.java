package com.green.websocket;

import javax.websocket.Session;
import java.util.concurrent.ConcurrentHashMap;

public class SessionRegistry {
    private static final ConcurrentHashMap<String, Session> SESSIONS = new ConcurrentHashMap<>();
    public static void register(String userId, Session session) { SESSIONS.put(userId, session); }
    public static void remove(String userId) { SESSIONS.remove(userId); }
    public static Session get(String userId) { return SESSIONS.get(userId); }
}