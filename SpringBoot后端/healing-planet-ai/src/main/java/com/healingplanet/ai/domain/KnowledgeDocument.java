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
        Instant createdAt,
        Map<String, String> attributes
) {
    public KnowledgeDocument {
        tags = tags == null ? List.of() : List.copyOf(tags);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public Map<String, Object> metadata() {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("sourceId", sourceId);
        result.put("sourceType", source.name());
        result.put("title", title);
        result.put("canonicalPlantId", canonicalPlantId == null ? "" : canonicalPlantId);
        result.put("plantName", plantName == null ? "" : plantName);
        result.put("knowledgeType", knowledgeType == null ? "" : knowledgeType);
        result.put("tags", String.join(",", tags));
        result.put("trustScore", trustScore);
        result.put("essence", essence);
        result.put("likes", likes);
        result.put("collects", collects);
        result.put("comments", comments);
        result.put("views", views);
        result.put("createdAt", createdAt == null ? "" : createdAt.toString());
        result.putAll(attributes);
        return Map.copyOf(result);
    }
}
