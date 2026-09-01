package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.KnowledgeDocument;
import com.healingplanet.ai.domain.KnowledgeSource;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
class KnowledgeDocumentMapper {

    KnowledgeDocument fromSpring(Document document, KnowledgeSource source) {
        Map<String, Object> m = document.getMetadata();
        String tagsValue = string(m, "tags");
        List<String> tags = tagsValue.isBlank() ? List.of() : Arrays.stream(tagsValue.split(",")).toList();
        String createdAt = string(m, "createdAt");
        return new KnowledgeDocument(
                document.getId(), source, string(m, "sourceId"), string(m, "title"), document.getText(),
                string(m, "canonicalPlantId"), string(m, "plantName"), string(m, "knowledgeType"), tags,
                number(m, "trustScore").doubleValue(), bool(m, "essence"),
                number(m, "likes").intValue(), number(m, "collects").intValue(),
                number(m, "comments").intValue(), number(m, "views").intValue(),
                createdAt.isBlank() ? null : Instant.parse(createdAt), attributes(m)
        );
    }

    private String string(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value == null ? "" : value.toString();
    }

    private Number number(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Number number) return number;
        if (value == null || value.toString().isBlank()) return 0;
        return Double.parseDouble(value.toString());
    }

    private boolean bool(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private Map<String, String> attributes(Map<String, Object> metadata) {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        for (String key : List.of("aliases", "visualSymptoms", "triggerConditions",
                "environmentConditions", "source", "sourceLevel", "diseaseId", "chunkIndex",
                "chunkCount", "section", "logicalEvidenceId", "fragmentId", "fragmentIndex",
                "fragmentCount", "fragmentRole", "fragmentSection", "contentHash", "sourceUpdatedAt", "indexVersion",
                "resolvedPlantIds", "plantEntityConfidence")) {
            Object value = metadata.get(key);
            if (value != null && !value.toString().isBlank()) result.put(key, value.toString());
        }
        return Map.copyOf(result);
    }
}
