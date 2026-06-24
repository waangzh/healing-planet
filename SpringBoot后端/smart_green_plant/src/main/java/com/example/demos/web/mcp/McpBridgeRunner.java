package com.example.demos.web.mcp;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class McpBridgeRunner implements CommandLineRunner {

    @Override
    public void run(String... args) throws Exception {
        String upstreamUrl = "wss://api.xiaozhi.me/mcp/?token=eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjI3NDUyMiwiYWdlbnRJZCI6MjQ4NDI3LCJlbmRwb2ludElkIjoiYWdlbnRfMjQ4NDI3IiwicHVycG9zZSI6Im1jcC1lbmRwb2ludCIsImlhdCI6MTc1NzkyMTgyNiwiZXhwIjoxNzg5NDc5NDI2fQ.94j6Aka9-ur_T2v_XCx1vL9fEhFlesjQaauWd89X8wF1UjHHvCvPhmcBhpHPGkYyUjcsqHjHIXxB3IVUxJSVGA";
        String localUrl = "ws://localhost:8080/mcp";

        McpBridge bridge = new McpBridge();
        bridge.startBridge(upstreamUrl, localUrl);
    }
}
