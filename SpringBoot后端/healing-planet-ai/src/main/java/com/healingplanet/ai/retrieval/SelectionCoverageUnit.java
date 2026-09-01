package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.Evidence;
import com.healingplanet.ai.domain.EvidenceType;
import com.healingplanet.ai.domain.KnowledgeSource;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A final-selection requirement derived from one query group. Keeping the group on every unit prevents a
 * topic or source found for one entity from satisfying another entity's required coverage.
 */
record SelectionCoverageUnit(String groupId, Kind kind, KnowledgeSource source, String topic) {

    enum Kind { GROUP, SOURCE, TOPIC }

    static List<SelectionCoverageUnit> forFinalSelection(RetrievalRequest request) {
        LinkedHashSet<SelectionCoverageUnit> units = new LinkedHashSet<>();
        for (RetrievalQueryGroup group : request.plan().queryGroups()) {
            if (!group.requiredCoverage()) continue;
            int before = units.size();
            if (request.plan().searchKnowledge() && group.sourceScope().knowledge()) {
                group.topicHints().stream().filter(topic -> topic != null && !topic.isBlank())
                        .map(topic -> topic.toUpperCase(Locale.ROOT))
                        .forEach(topic -> units.add(new SelectionCoverageUnit(group.id(), Kind.TOPIC,
                                KnowledgeSource.PLANT, topic)));
            }
            if (request.sourcePlan().knowledge().required() && group.sourceScope().knowledge()) {
                units.add(new SelectionCoverageUnit(group.id(), Kind.SOURCE, KnowledgeSource.PLANT, ""));
            }
            if (request.sourcePlan().community().required() && group.sourceScope().community()) {
                units.add(new SelectionCoverageUnit(group.id(), Kind.SOURCE, KnowledgeSource.COMMUNITY, ""));
            }
            if (units.size() == before) {
                units.add(new SelectionCoverageUnit(group.id(), Kind.GROUP, null, ""));
            }
        }
        return List.copyOf(units);
    }

    static List<SelectionCoverageUnit> forRerankAdmission(RetrievalRequest request) {
        LinkedHashSet<SelectionCoverageUnit> units = new LinkedHashSet<>();
        for (RetrievalQueryGroup group : request.plan().queryGroups()) {
            if (!group.requiredCoverage()) continue;
            units.add(new SelectionCoverageUnit(group.id(), Kind.GROUP, null, ""));
            if (request.sourcePlan().knowledge().required() && group.sourceScope().knowledge()) {
                units.add(new SelectionCoverageUnit(group.id(), Kind.SOURCE, KnowledgeSource.PLANT, ""));
            }
            if (request.sourcePlan().community().required() && group.sourceScope().community()) {
                units.add(new SelectionCoverageUnit(group.id(), Kind.SOURCE, KnowledgeSource.COMMUNITY, ""));
            }
        }
        return List.copyOf(units);
    }

    boolean matches(Evidence evidence, Map<String, RetrievalQueryGroup> groups) {
        RetrievalQueryGroup group = groups.get(groupId);
        if (!GroupCoverageMatcher.matches(evidence, group)) return false;
        return switch (kind) {
            case GROUP -> true;
            case SOURCE -> source == sourceOf(evidence);
            case TOPIC -> source == KnowledgeSource.PLANT && sourceOf(evidence) == KnowledgeSource.PLANT
                    && topic.equals(metadata(evidence, "knowledgeType").toUpperCase(Locale.ROOT));
        };
    }

    boolean matches(LogicalEvidenceCandidate candidate, Map<String, RetrievalQueryGroup> groups) {
        RetrievalQueryGroup group = groups.get(groupId);
        if (!GroupCoverageMatcher.matches(candidate, group)) return false;
        return kind != Kind.SOURCE || source == candidate.representative().source();
    }

    static Map<String, RetrievalQueryGroup> indexedGroups(RetrievalRequest request) {
        Map<String, RetrievalQueryGroup> groups = new LinkedHashMap<>();
        request.plan().queryGroups().forEach(group -> groups.put(group.id(), group));
        return Map.copyOf(groups);
    }

    private static KnowledgeSource sourceOf(Evidence evidence) {
        return evidence.type() == EvidenceType.CARE_GUIDE ? KnowledgeSource.PLANT
                : evidence.type() == EvidenceType.COMMUNITY_POST ? KnowledgeSource.COMMUNITY : null;
    }

    private static String metadata(Evidence evidence, String key) {
        Object value = evidence.metadata().get(key);
        return value == null ? "" : value.toString();
    }
}
