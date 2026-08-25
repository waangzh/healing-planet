package com.healingplanet.ai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 共享数据库是配置真相源；各实例只在 ACTIVE revision 变化时本地探测并原子切换。 */
@Component
@ConditionalOnProperty(prefix = "app.rag", name = "config-sync-enabled", havingValue = "true", matchIfMissing = true)
public class RagRuntimeConfigSynchronizer {
    private static final Logger log = LoggerFactory.getLogger(RagRuntimeConfigSynchronizer.class);
    private final RagConfigService configService;

    public RagRuntimeConfigSynchronizer(RagConfigService configService) {
        this.configService = configService;
    }

    @Scheduled(fixedDelayString = "${app.rag.config-sync-interval-ms:5000}",
            initialDelayString = "${app.rag.config-sync-initial-delay-ms:10000}")
    public void synchronize() {
        try {
            if (configService.refreshActiveRuntime()) {
                log.info("已同步 RAG 运行时配置版本 v{}", configService.current().revision());
            }
        } catch (RuntimeException exception) {
            // 保留旧快照，下一周期重试；不记录异常原文，避免第三方连接信息进入日志。
            log.warn("RAG 配置同步失败，保留当前运行时快照：{}", exception.getMessage());
        }
    }
}
