package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.KnowledgeSource;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Per-group required-source gaps after the first rerank pass. */
record QualifiedRecallCoverage(Map<String, Set<KnowledgeSource>> missingRequiredSourcesByGroup) {
    QualifiedRecallCoverage {
        Map<String, Set<KnowledgeSource>> copy = new LinkedHashMap<>();
        (missingRequiredSourcesByGroup == null ? Map.<String, Set<KnowledgeSource>>of()
                : missingRequiredSourcesByGroup).forEach((groupId, sources) ->
                copy.put(groupId, Collections.unmodifiableSet(new LinkedHashSet<>(sources == null ? Set.of() : sources))));
        missingRequiredSourcesByGroup = Collections.unmodifiableMap(copy);
    }

    boolean sufficient() {
        return missingRequiredSourcesByGroup.values().stream().allMatch(Set::isEmpty);
    }

    Set<KnowledgeSource> missingRequiredSources() {
        Set<KnowledgeSource> result = new LinkedHashSet<>();
        missingRequiredSourcesByGroup.values().forEach(result::addAll);
        return result;
    }
}
