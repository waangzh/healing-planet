package com.healingplanet.ai.domain;

import java.time.Instant;
import java.util.Map;

public record Evidence(
        String id,
        EvidenceType type,
        String sourceId,
        String sourceType,
        String title,
        String content,
        Double retrievalScore,
        Double rerankScore,
        Double trustScore,
        Double finalScore,
        Map<String, Object> metadata,
        Instant timestamp
) {
    public Evidence {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public Evidence withScores(Double rerank, double score) {
        return new Evidence(id, type, sourceId, sourceType, title, content,
                retrievalScore, rerank, trustScore, score, metadata, timestamp);
    }
}
