package com.example.demos.web.mcp;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;

public class McpBridge {

    private WebSocketClient upstreamClient;  // 小智 MCP
    private WebSocketClient localClient;     // 本地 MCP

    public void startBridge(String upstreamUrl, String localUrl) throws Exception {
        // 连接上游 MCP
        upstreamClient = new WebSocketClient(new URI(upstreamUrl)) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                System.out.println("连接到小智 MCP");
            }

            @Override
            public void onMessage(String message) {
                System.out.println("来自小智MCP: " + message);
                if (localClient != null && localClient.isOpen()) {
                    localClient.send(message);
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                System.out.println("小智MCP服务关闭: " + reason);
            }

            @Override
            public void onError(Exception ex) {
                ex.printStackTrace();
            }
        };

        // 连接本地 MCP
        localClient = new WebSocketClient(new URI(localUrl)) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                System.out.println("连接到本地MCP服务");
            }

            @Override
            public void onMessage(String message) {
                System.out.println("来自本地MCP服务: " + message);
                if (upstreamClient != null && upstreamClient.isOpen()) {
                    upstreamClient.send(message);
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                System.out.println("本地MCP服务关闭: " + reason);
            }

            @Override
            public void onError(Exception ex) {
                ex.printStackTrace();
            }
        };

        upstreamClient.connect();
        localClient.connect();
    }
}
