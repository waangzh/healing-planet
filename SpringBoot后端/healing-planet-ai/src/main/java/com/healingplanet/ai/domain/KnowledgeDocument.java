package com.healingplanet.ai.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record KnowledgeDocument(
        String id,
        KnowledgeSource source,
        String sourceId,
        String title,
        String embeddingText,
        String displayContent,
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
    private static final java.util.Set<String> COMMUNITY_DYNAMIC_RANKING_FIELDS = java.util.Set.of(
            "trustScore", "essence", "likes", "collects", "comments", "views");

    public KnowledgeDocument {
        embeddingText = embeddingText == null ? "" : embeddingText;
        displayContent = displayContent == null ? "" : displayContent;
        tags = tags == null ? List.of() : List.copyOf(tags);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /** 兼容旧调用方；旧内容同时承担索引和展示职责。 */
    public KnowledgeDocument(String id, KnowledgeSource source, String sourceId, String title, String content,
                             String canonicalPlantId, String plantName, String knowledgeType, List<String> tags,
                             double trustScore, boolean essence, int likes, int collects, int comments, int views,
                             Instant createdAt, Map<String, String> attributes) {
        this(id, source, sourceId, title, content, content, canonicalPlantId, plantName, knowledgeType, tags,
                trustScore, essence, likes, collects, comments, views, createdAt, attributes);
    }

    /**
     * 兼容仍以 content 命名的检索追踪与内部调用；生成层应显式使用 displayContent。
     */
    public String content() {
        return displayContent;
    }

    public Map<String, Object> metadata() {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("sourceId", sourceId);
        result.put("sourceType", source.name());
        result.put("title", title);
        result.put("displayContent", displayContent);
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

    /**
     * Qdrant payload only carries fields that affect retrieval, attribution, display, or static source semantics.
     * Community engagement is hydrated after recall from the business database, so a view increment never rewrites
     * the vector store.
     */
    /**
     * Canonical static retrieval metadata contract shared by Qdrant payloads and Lucene's stored metadata.
     * Runtime community engagement stays outside it because it is hydrated from the business database after recall.
     */
    public Map<String, Object> retrievalMetadata() {
        Map<String, Object> result = new java.util.LinkedHashMap<>(metadata());
        result.remove("payloadHash");
        if (source == KnowledgeSource.COMMUNITY) {
            COMMUNITY_DYNAMIC_RANKING_FIELDS.forEach(result::remove);
        }
        return Map.copyOf(result);
    }

    /** Compatibility name for callers that write the canonical retrieval metadata to a vector payload. */
    public Map<String, Object> vectorPayloadMetadata() {
        return retrievalMetadata();
    }
}
