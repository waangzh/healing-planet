package com.healingplanet.ai.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record KnowledgeDocument(
        String id,
        KnowledgeSource source,
        String sourceId,
        String title,
        String content,
        String canonicalPlantId,
        String plantName,
        String knowledgeType,
        List<String> tags,
        double trustScore,
        boolean essence,
        int likes,
        int collects,
        int comments,
        int views,
        Instant createdAt
) {
    public KnowledgeDocument {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public Map<String, Object> metadata() {
        return Map.ofEntries(
                Map.entry("sourceId", sourceId),
                Map.entry("sourceType", source.name()),
                Map.entry("title", title),
                Map.entry("canonicalPlantId", canonicalPlantId == null ? "" : canonicalPlantId),
                Map.entry("plantName", plantName == null ? "" : plantName),
                Map.entry("knowledgeType", knowledgeType == null ? "" : knowledgeType),
                Map.entry("tags", String.join(",", tags)),
                Map.entry("trustScore", trustScore),
                Map.entry("essence", essence),
                Map.entry("likes", likes),
                Map.entry("collects", collects),
                Map.entry("comments", comments),
                Map.entry("views", views),
                Map.entry("createdAt", createdAt == null ? "" : createdAt.toString())
        );
    }
}
