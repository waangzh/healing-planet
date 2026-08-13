package com.healingplanet.ai.domain;

import java.util.List;
import java.util.Map;

public record RagQuery(
        String query,
        Long userId,
        Long plantInstanceId,
        String canonicalPlantId,
        QueryIntent intent,
        List<String> keywords,
        Map<String, Object> context
) {
    public RagQuery {
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        context = context == null ? Map.of() : Map.copyOf(context);
    }

    public static RagQuery of(String query) {
        return new RagQuery(query, null, null, null, null, List.of(), Map.of());
    }
}
