package com.healingplanet.ai.config;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/** 发布成功后一次替换整个不可变快照。 */
@Component
public class RagRuntimeConfigProvider {
    private final AtomicReference<RagRuntimeConfig> active;

    public RagRuntimeConfigProvider(RagProperties properties) {
        this.active = new AtomicReference<>(RagRuntimeConfig.from(properties));
    }

    public RagRuntimeConfig snapshot() {
        return active.get();
    }

    public void activate(RagRuntimeConfig config) {
        active.set(config);
    }
}
