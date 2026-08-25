package com.healingplanet.ai.config;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.atomic.AtomicReference;

/** 发布成功后一次替换整个不可变快照。 */
@Component
public class RagRuntimeConfigProvider {
    private final AtomicReference<RagRuntimeSnapshot> active;

    @Autowired
    public RagRuntimeConfigProvider(RagProperties properties, RagExternalClientManager clientManager) {
        this.active = new AtomicReference<>(clientManager.buildWithoutProbe(RagRuntimeConfig.from(properties)));
    }

    /** 仅供轻量单元测试构造；生产环境总是使用包含客户端的构造器。 */
    public RagRuntimeConfigProvider(RagProperties properties) {
        this.active = new AtomicReference<>(new RagRuntimeSnapshot(RagRuntimeConfig.from(properties), null));
    }

    public RagRuntimeConfig snapshot() {
        return active.get().config();
    }

    public RagRuntimeSnapshot runtimeSnapshot() {
        return active.get();
    }

    public void activate(RagRuntimeConfig config) {
        active.updateAndGet(current -> new RagRuntimeSnapshot(config, current.rerankerClient()));
    }

    public void activate(RagRuntimeSnapshot snapshot) {
        active.set(snapshot);
    }
}
