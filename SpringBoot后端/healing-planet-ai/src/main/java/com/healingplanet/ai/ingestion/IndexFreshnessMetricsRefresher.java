package com.healingplanet.ai.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically refreshes freshness gauges through the same read-only status path exposed to operators. */
@Component
public class IndexFreshnessMetricsRefresher {
    private static final Logger log = LoggerFactory.getLogger(IndexFreshnessMetricsRefresher.class);
    private final IndexStatusService indexStatusService;

    public IndexFreshnessMetricsRefresher(IndexStatusService indexStatusService) {
        this.indexStatusService = indexStatusService;
    }

    @Scheduled(fixedDelayString = "${app.rag.index-observability.freshness-refresh-interval:60000}",
            initialDelayString = "${app.rag.index-observability.freshness-refresh-initial-delay:15000}")
    public void refresh() {
        try {
            indexStatusService.status();
        } catch (RuntimeException exception) {
            log.warn("刷新 RAG 索引新鲜度指标失败", exception);
        }
    }
}
