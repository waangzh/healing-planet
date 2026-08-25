package com.healingplanet.ai.config;

/** 管理端可见的安全连接 profile，不包含地址细节和密钥。 */
public record RagConnectionProfileView(String id, String label) {
}
