package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.KnowledgeSource;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Facts observed after recall, before reranking and evidence selection. */
public record RecallCoverage(
        Map<String, QueryGroupCoverage> groups,
        Set<KnowledgeSource> coveredRequiredSources,
        Set<String> coveredRequiredQueryGroups,
        Set<String> coveredEntities,
        Set<String> coveredTopics,
        int uniqueLogicalCandidates,
        Set<KnowledgeSource> missingRequiredSources,
        Set<String> missingRequiredQueryGroups,
        Set<String> missingEntities,
        Set<String> missingTopics,
        int minimumUniqueLogicalCandidates
) {
    public RecallCoverage {
        groups = immutableMap(groups);
        coveredRequiredSources = immutable(coveredRequiredSources);
        coveredRequiredQueryGroups = immutable(coveredRequiredQueryGroups);
        coveredEntities = immutable(coveredEntities);
        coveredTopics = immutable(coveredTopics);
        missingRequiredSources = immutable(missingRequiredSources);
        missingRequiredQueryGroups = immutable(missingRequiredQueryGroups);
        missingEntities = immutable(missingEntities);
        missingTopics = immutable(missingTopics);
        uniqueLogicalCandidates = Math.max(0, uniqueLogicalCandidates);
        minimumUniqueLogicalCandidates = Math.max(0, minimumUniqueLogicalCandidates);
    }

    public boolean sufficient() {
        return missingRequiredSources.isEmpty() && missingRequiredQueryGroups.isEmpty()
                && missingEntities.isEmpty() && missingTopics.isEmpty()
                && uniqueLogicalCandidates >= minimumUniqueLogicalCandidates;
    }

    private static <T> Set<T> immutable(Set<T> values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(values == null ? Set.of() : values));
    }

    private static Map<String, QueryGroupCoverage> immutableMap(Map<String, QueryGroupCoverage> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values == null ? Map.of() : values));
    }
}
