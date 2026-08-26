package com.green.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.post-index-outbox")
public class PostIndexOutboxProperties {
    private boolean enabled = true;
    private int publishFixedDelayMillis = 5000;
    private int batchSize = 50;
    private long leaseMillis = 30000;
    private long initialRetryDelayMillis = 5000;
    private long maxRetryDelayMillis = 300000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getPublishFixedDelayMillis() { return publishFixedDelayMillis; }
    public void setPublishFixedDelayMillis(int publishFixedDelayMillis) { this.publishFixedDelayMillis = publishFixedDelayMillis; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public long getLeaseMillis() { return leaseMillis; }
    public void setLeaseMillis(long leaseMillis) { this.leaseMillis = leaseMillis; }
    public long getInitialRetryDelayMillis() { return initialRetryDelayMillis; }
    public void setInitialRetryDelayMillis(long initialRetryDelayMillis) { this.initialRetryDelayMillis = initialRetryDelayMillis; }
    public long getMaxRetryDelayMillis() { return maxRetryDelayMillis; }
    public void setMaxRetryDelayMillis(long maxRetryDelayMillis) { this.maxRetryDelayMillis = maxRetryDelayMillis; }
}
