package com.healingplanet.ai.domain;

/**
 * Fragment 在逻辑证据中的角色。当前摄取只写入 CONTENT，MAJOR 预留给父级摘要/大纲文档。
 */
public enum FragmentRole {
    MAJOR,
    CONTENT
}
