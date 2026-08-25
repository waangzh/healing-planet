package com.green.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.rag-admin")
public class RagAdminProperties {
    private String aiBaseUrl = "http://localhost:8010";
    private String internalApiKey = "";
    private int connectTimeoutMillis = 3000;
    private int readTimeoutMillis = 10000;

    public String getAiBaseUrl() { return aiBaseUrl; }
    public void setAiBaseUrl(String aiBaseUrl) { this.aiBaseUrl = aiBaseUrl; }
    public String getInternalApiKey() { return internalApiKey; }
    public void setInternalApiKey(String internalApiKey) { this.internalApiKey = internalApiKey; }
    public int getConnectTimeoutMillis() { return connectTimeoutMillis; }
    public void setConnectTimeoutMillis(int connectTimeoutMillis) { this.connectTimeoutMillis = connectTimeoutMillis; }
    public int getReadTimeoutMillis() { return readTimeoutMillis; }
    public void setReadTimeoutMillis(int readTimeoutMillis) { this.readTimeoutMillis = readTimeoutMillis; }
}
