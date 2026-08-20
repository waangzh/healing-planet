package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.Evidence;
import com.healingplanet.ai.domain.EvidenceType;
import com.healingplanet.ai.domain.QueryIntent;
import com.healingplanet.ai.domain.RagQuery;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Selects prompt evidence from already ranked candidates. Retrieval and scoring stay
 * outside this component so that selection can optimize metadata-aware coverage.
 */
@Component
public class EvidenceSelector {
    private static final int MIXED_SOURCE_COMMUNITY_LIMIT = 2;

    public Selection select(RagQuery query, List<Evidence> ranked, int maxEvidenceItems,
                            List<String> canonicalPlantIds) {
        if (maxEvidenceItems <= 0 || ranked.isEmpty()) {
            return new Selection(List.of(), Map.of());
        }

        SelectionState state = new SelectionState(maxEvidenceItems, isMixedSourceQuery(query));
        Set<String> requiredTypes = requiredKnowledgeTypes(query);

        retainSourceCoverage(ranked, state);
        retainRequiredTopicCoverage(ranked, requiredTypes, state);
        retainEntityCoverage(ranked, canonicalPlantIds, state);
        if (requiredTypes.isEmpty() && query.intent() == QueryIntent.GENERAL_CARE) {
            retainBroadCareCoverage(ranked, state);
        }
        ranked.forEach(evidence -> state.add(evidence, "GLOBAL_RANKING"));

        List<Evidence> selected = state.items().values().stream()
                .sorted(Comparator.comparing(Evidence::finalScore,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        Map<String, String> reasons = new LinkedHashMap<>();
        selected.forEach(evidence -> reasons.put(evidence.id(), state.reasons().get(evidence.id())));
        return new Selection(selected, Map.copyOf(reasons));
    }

    private void retainSourceCoverage(List<Evidence> ranked, SelectionState state) {
        if (!state.mixedSource()) return;
        ranked.stream().filter(this::isPlant).findFirst()
                .ifPresent(evidence -> state.add(evidence, "SOURCE_RETENTION"));
        int communityLimit = Math.min(MIXED_SOURCE_COMMUNITY_LIMIT, Math.max(0, state.capacity() - 1));
        for (Evidence evidence : ranked) {
            if (state.communitySourceCount() >= communityLimit) break;
            if (isCommunity(evidence)) {
                state.add(evidence, "SOURCE_RETENTION");
            }
        }
    }

    private void retainRequiredTopicCoverage(List<Evidence> ranked, Set<String> requiredTypes,
                                             SelectionState state) {
        for (String type : requiredTypes) {
            ranked.stream().filter(this::isPlant).filter(evidence -> type.equals(knowledgeType(evidence)))
                    .findFirst().ifPresent(evidence -> state.add(evidence, "TOPIC_COVERAGE"));
        }
    }

    private void retainEntityCoverage(List<Evidence> ranked, List<String> canonicalPlantIds,
                                      SelectionState state) {
        if (canonicalPlantIds == null || canonicalPlantIds.size() < 2) return;
        for (String canonicalPlantId : canonicalPlantIds) {
            ranked.stream().filter(this::isPlant)
                    .filter(evidence -> canonicalPlantId.equals(metadata(evidence, "canonicalPlantId")))
                    .findFirst().ifPresent(evidence -> state.add(evidence, "ENTITY_QUOTA"));
        }
    }

    private void retainBroadCareCoverage(List<Evidence> ranked, SelectionState state) {
        Set<String> coveredTopics = new LinkedHashSet<>();
        for (Evidence evidence : ranked) {
            if (!isPlant(evidence) || knowledgeType(evidence).isEmpty()) continue;
            String topicKey = metadata(evidence, "canonicalPlantId") + "\u0000" + knowledgeType(evidence);
            if (coveredTopics.add(topicKey)) {
                state.add(evidence, "BROAD_CARE_COVERAGE");
            }
        }
    }

    private Set<String> requiredKnowledgeTypes(RagQuery query) {
        Set<String> result = new LinkedHashSet<>();
        addKnowledgeType(result, query.context().get("requiredKnowledgeType"));
        Object multiple = query.context().get("requiredKnowledgeTypes");
        if (multiple instanceof Iterable<?> values) {
            values.forEach(value -> addKnowledgeType(result, value));
        }
        return result;
    }

    private void addKnowledgeType(Set<String> types, Object value) {
        if (value instanceof String type && !type.isBlank()) {
            types.add(type.toUpperCase(Locale.ROOT));
        }
    }

    private boolean isMixedSourceQuery(RagQuery query) {
        return booleanContext(query, "includePlantKnowledge", query.intent() != QueryIntent.COMMUNITY_SEARCH)
                && booleanContext(query, "includeCommunity", true);
    }

    private boolean booleanContext(RagQuery query, String key, boolean defaultValue) {
        Object value = query.context().get(key);
        return value instanceof Boolean bool ? bool : defaultValue;
    }

    private boolean isPlant(Evidence evidence) {
        return evidence.type() == EvidenceType.CARE_GUIDE;
    }

    private boolean isCommunity(Evidence evidence) {
        return evidence.type() == EvidenceType.COMMUNITY_POST;
    }

    private String knowledgeType(Evidence evidence) {
        return metadata(evidence, "knowledgeType").toUpperCase(Locale.ROOT);
    }

    private String metadata(Evidence evidence, String key) {
        Object value = evidence.metadata().get(key);
        return value == null ? "" : value.toString();
    }

    public record Selection(List<Evidence> evidence, Map<String, String> reasons) { }

    private final class SelectionState {
        private final int capacity;
        private final boolean mixedSource;
        private final Map<String, Evidence> items = new LinkedHashMap<>();
        private final Map<String, String> reasons = new LinkedHashMap<>();
        private final Set<String> logicalGroups = new LinkedHashSet<>();
        private final Set<String> communitySourceIds = new LinkedHashSet<>();

        private SelectionState(int capacity, boolean mixedSource) {
            this.capacity = capacity;
            this.mixedSource = mixedSource;
        }

        private void add(Evidence evidence, String reason) {
            if (items.containsKey(evidence.id()) || items.size() >= capacity || !canAdd(evidence)) return;
            items.put(evidence.id(), evidence);
            reasons.put(evidence.id(), reason);
            logicalGroups.add(logicalGroup(evidence));
            if (isCommunity(evidence)) communitySourceIds.add(sourceKey(evidence));
        }

        private boolean canAdd(Evidence evidence) {
            if (logicalGroups.contains(logicalGroup(evidence))) return false;
            return !mixedSource || !isCommunity(evidence)
                    || !communitySourceIds.contains(sourceKey(evidence))
                    && communitySourceIds.size() < Math.min(MIXED_SOURCE_COMMUNITY_LIMIT, Math.max(0, capacity - 1));
        }

        private String logicalGroup(Evidence evidence) {
            return sourceKey(evidence) + "\u0000" + knowledgeType(evidence);
        }

        private String sourceKey(Evidence evidence) {
            return evidence.sourceId() == null || evidence.sourceId().isBlank() ? evidence.id() : evidence.sourceId();
        }

        private int capacity() { return capacity; }
        private boolean mixedSource() { return mixedSource; }
        private int communitySourceCount() { return communitySourceIds.size(); }
        private Map<String, Evidence> items() { return items; }
        private Map<String, String> reasons() { return reasons; }
    }
}
