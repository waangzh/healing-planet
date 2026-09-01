package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.KnowledgeDocument;
import com.healingplanet.ai.domain.KnowledgeSource;
import com.healingplanet.ai.domain.Evidence;
import com.healingplanet.ai.domain.EvidenceType;

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

    static boolean matches(Evidence evidence, RetrievalQueryGroup group) {
        if (evidence == null || group == null || !matchedQueryGroupIds(evidence).contains(group.id())) return false;
        if (group.canonicalPlantIds().isEmpty()) return true;
        if (evidence.type() == EvidenceType.CARE_GUIDE) {
            return group.canonicalPlantIds().contains(metadata(evidence, "canonicalPlantId"));
        }
        return evidence.type() == EvidenceType.COMMUNITY_POST
                && resolvedPlantIds(evidence).containsAll(group.canonicalPlantIds());
    }

    private static Set<String> matchedQueryGroupIds(Evidence evidence) {
        return commaSeparated(metadata(evidence, "matchedQueryGroupIds"));
    }

    private static Set<String> resolvedPlantIds(Evidence evidence) {
        Set<String> result = commaSeparated(metadata(evidence, "resolvedPlantIds"));
        String canonicalPlantId = metadata(evidence, "canonicalPlantId");
        if (!canonicalPlantId.isBlank()) result.add(canonicalPlantId);
        return result;
    }

    private static Set<String> commaSeparated(String value) {
        Set<String> result = new LinkedHashSet<>();
        for (String item : value.split(",")) {
            if (item != null && !item.isBlank()) result.add(item.trim());
        }
        return result;
    }

    private static String metadata(Evidence evidence, String key) {
        Object value = evidence.metadata().get(key);
        return value == null ? "" : value.toString();
    }
}
