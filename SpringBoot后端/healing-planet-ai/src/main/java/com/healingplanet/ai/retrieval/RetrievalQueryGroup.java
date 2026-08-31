package com.healingplanet.ai.retrieval;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A deterministic recall facet. It carries filtering and coverage semantics,
 * rather than asking an LLM to decompose a request at query time.
 */
public record RetrievalQueryGroup(
        String id,
        String query,
        GroupRole role,
        Set<String> topicHints,
        Set<String> canonicalPlantIds,
        SourceScope sourceScope,
        boolean requiredCoverage
) {
    public RetrievalQueryGroup {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id 不能为空");
        query = query == null ? "" : query;
        role = role == null ? GroupRole.PRIMARY : role;
        topicHints = immutable(topicHints);
        canonicalPlantIds = immutable(canonicalPlantIds);
        sourceScope = sourceScope == null ? new SourceScope(false, false) : sourceScope;
    }

    public RetrievalQueryGroup withQuery(String value) {
        return new RetrievalQueryGroup(id, value, role, topicHints, canonicalPlantIds, sourceScope, requiredCoverage);
    }

    private static Set<String> immutable(Set<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values != null) {
            values.stream().filter(value -> value != null && !value.isBlank())
                    .map(String::trim).forEach(result::add);
        }
        return Collections.unmodifiableSet(result);
    }
}
