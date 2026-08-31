package com.healingplanet.ai.domain;

import java.util.Map;
import java.util.Objects;

/**
 * 写入检索索引的物理片段。缺少新元数据的旧索引文档会退化为单片段逻辑证据。
 */
public record EvidenceFragment(String id, LogicalEvidence logicalEvidence,
                               FragmentRole role, int index, int count, String section) {

    public EvidenceFragment {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("fragment id 不能为空");
        }
        logicalEvidence = Objects.requireNonNull(logicalEvidence, "logical evidence 不能为空");
        role = role == null ? FragmentRole.CONTENT : role;
        if (index < 0 || count < 1 || index >= count) {
            throw new IllegalArgumentException("fragment index/count 非法");
        }
        section = section == null ? "" : section;
    }

    public static EvidenceFragment from(KnowledgeDocument document) {
        Objects.requireNonNull(document, "knowledge document 不能为空");
        Map<String, String> attributes = document.attributes();
        String fragmentId = valueOrDefault(attributes, "fragmentId", document.id());
        int count = positiveInt(attributes, "fragmentCount", 1);
        int index = nonNegativeInt(attributes, "fragmentIndex", 0);
        if (index >= count) {
            index = 0;
            count = 1;
        }
        return new EvidenceFragment(fragmentId, LogicalEvidence.from(document), fragmentRole(attributes), index, count,
                valueOrDefault(attributes, "fragmentSection", attributes.getOrDefault("section", "")));
    }

    private static String valueOrDefault(Map<String, String> attributes, String key, String fallback) {
        String value = attributes.get(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int positiveInt(Map<String, String> attributes, String key, int fallback) {
        int value = nonNegativeInt(attributes, key, fallback);
        return value < 1 ? fallback : value;
    }

    private static int nonNegativeInt(Map<String, String> attributes, String key, int fallback) {
        String value = attributes.get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed < 0 ? fallback : parsed;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static FragmentRole fragmentRole(Map<String, String> attributes) {
        String value = attributes.get("fragmentRole");
        if (value == null || value.isBlank()) {
            return FragmentRole.CONTENT;
        }
        try {
            return FragmentRole.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return FragmentRole.CONTENT;
        }
    }
}
