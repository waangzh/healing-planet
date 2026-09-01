package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.KnowledgeSource;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Structural recall coverage for one deterministic query group. */
public record QueryGroupCoverage(
        String groupId,
        Set<String> coveredEntities,
        Set<String> missingEntities,
        Set<String> coveredTopics,
        Set<String> missingTopics,
        Set<KnowledgeSource> coveredSources,
        Set<KnowledgeSource> missingSources,
        int uniqueLogicalCandidates
) {
    public QueryGroupCoverage {
        coveredEntities = immutable(coveredEntities);
        missingEntities = immutable(missingEntities);
        coveredTopics = immutable(coveredTopics);
        missingTopics = immutable(missingTopics);
        coveredSources = immutable(coveredSources);
        missingSources = immutable(missingSources);
        uniqueLogicalCandidates = Math.max(0, uniqueLogicalCandidates);
    }

    public boolean sufficient() {
        return uniqueLogicalCandidates > 0 && missingEntities.isEmpty() && missingTopics.isEmpty()
                && missingSources.isEmpty();
    }

    private static <T> Set<T> immutable(Set<T> values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(values == null ? Set.of() : values));
    }
}
