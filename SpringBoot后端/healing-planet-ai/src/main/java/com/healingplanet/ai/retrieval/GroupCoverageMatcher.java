package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.KnowledgeDocument;
import com.healingplanet.ai.domain.KnowledgeSource;

import java.util.LinkedHashSet;
import java.util.Set;

/** Keeps recall-route attribution from turning into unsupported entity attribution. */
final class GroupCoverageMatcher {
    private GroupCoverageMatcher() {
    }

    static boolean matches(LogicalEvidenceCandidate candidate, RetrievalQueryGroup group) {
        if (candidate == null || group == null || !candidate.matchedQueryGroupIds().contains(group.id())) {
            return false;
        }
        if (group.canonicalPlantIds().isEmpty()) {
            return true;
        }
        KnowledgeDocument document = candidate.representative();
        if (document.source() == KnowledgeSource.PLANT) {
            return group.canonicalPlantIds().contains(document.canonicalPlantId());
        }
        if (document.source() == KnowledgeSource.COMMUNITY) {
            return resolvedPlantIds(document).containsAll(group.canonicalPlantIds());
        }
        return false;
    }

    static Set<String> resolvedPlantIds(KnowledgeDocument document) {
        Set<String> result = new LinkedHashSet<>();
        if (document == null) return result;
        if (document.canonicalPlantId() != null && !document.canonicalPlantId().isBlank()) {
            result.add(document.canonicalPlantId());
        }
        String values = document.attributes().getOrDefault("resolvedPlantIds", "");
        for (String value : values.split(",")) {
            if (value != null && !value.isBlank()) result.add(value.trim());
        }
        return result;
    }
}
