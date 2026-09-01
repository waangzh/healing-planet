package com.healingplanet.ai.ingestion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 影响向量内容兼容性的版本契约。
 *
 * <p>模型、进入 embedding 的文本格式或分块策略任一变化，都必须产生不同指纹，
 * 从而使已有 fragment 在下一次受控索引中重新向量化。</p>
 */
public record IndexFingerprint(
        String embeddingModelVersion,
        String embeddingContentVersion,
        String chunkSchemaVersion
) {
    public IndexFingerprint {
        embeddingModelVersion = require("embedding-model-version", embeddingModelVersion);
        embeddingContentVersion = require("embedding-content-version", embeddingContentVersion);
        chunkSchemaVersion = require("chunk-schema-version", chunkSchemaVersion);
    }

    public String value() {
        return sha256(embeddingModelVersion + "\u0000" + embeddingContentVersion + "\u0000" + chunkSchemaVersion);
    }

    private static String require(String property, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("app.rag.ingestion." + property + " 不能为空");
        }
        return value.trim();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", exception);
        }
    }
}
