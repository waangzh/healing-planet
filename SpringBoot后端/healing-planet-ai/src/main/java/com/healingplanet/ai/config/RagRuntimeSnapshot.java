package com.healingplanet.ai.config;

import org.springframework.web.client.RestClient;

/** 一次请求使用的完整运行时快照，配置与可重载客户端保持在同一个原子引用中。 */
public record RagRuntimeSnapshot(RagRuntimeConfig config, RerankerRuntimeClient rerankerClient) {

    public record RerankerRuntimeClient(RestClient client, String path, String model, int candidateTopK,
                                        int maxFragmentsPerLogicalEvidence, int maxFragmentsTotal,
                                        String connectionId) {
    }
}
