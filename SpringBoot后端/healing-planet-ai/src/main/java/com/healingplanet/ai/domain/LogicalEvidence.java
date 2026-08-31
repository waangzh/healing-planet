package com.healingplanet.ai.domain;

import java.util.Objects;

/**
 * 可独立排序和呈现的逻辑证据。一个逻辑证据可由多个物理 fragment 支撑。
 */
public record LogicalEvidence(String id, KnowledgeSource source, String sourceId) {

    public LogicalEvidence {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("logical evidence id 不能为空");
        }
        source = Objects.requireNonNull(source, "logical evidence source 不能为空");
        sourceId = sourceId == null ? "" : sourceId;
    }

    public static LogicalEvidence from(KnowledgeDocument document) {
        Objects.requireNonNull(document, "knowledge document 不能为空");
        String id = document.attributes().get("logicalEvidenceId");
        return new LogicalEvidence(id == null || id.isBlank() ? document.id() : id,
                document.source(), document.sourceId());
    }
}
