package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.config.RagRuntimeConfig;
import com.healingplanet.ai.domain.Evidence;
import com.healingplanet.ai.domain.EvidenceType;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

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
    private final RagProperties properties;

    public EvidenceSelector() {
        this(new RagProperties());
    }

    @Autowired
    public EvidenceSelector(RagProperties properties) {
        this.properties = properties;
    }

    public Selection select(RetrievalRequest request, List<Evidence> ranked, int maxEvidenceItems,
                            List<String> canonicalPlantIds) {
        return select(request, ranked, maxEvidenceItems, canonicalPlantIds, RagRuntimeConfig.from(properties));
    }

    public Selection select(RetrievalRequest request, List<Evidence> ranked, int maxEvidenceItems,
                            List<String> canonicalPlantIds, RagRuntimeConfig config) {
        if (maxEvidenceItems <= 0 || ranked.isEmpty()) {
            return new Selection(List.of(), Map.of());
        }

        SelectionState state = new SelectionState(maxEvidenceItems, request.sourcePlan(),
                config.mixedSourceCommunityLimit());
        Set<String> topicHints = request.topicHints();

        retainRequiredQueryGroupCoverage(request.plan().queryGroups(), ranked, state);
        retainSourceCoverage(ranked, state);
        retainTopicHintCoverage(ranked, topicHints, state);
        retainEntityCoverage(ranked, canonicalPlantIds, state);
        if (topicHints.isEmpty() && request.analysis().intentHint() == com.healingplanet.ai.domain.QueryIntent.GENERAL_CARE) {
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
        if (state.sourcePlan().knowledge().required()) {
            ranked.stream().filter(this::isPlant).findFirst()
                    .ifPresent(evidence -> state.add(evidence, "SOURCE_RETENTION"));
        }
        if (!state.sourcePlan().community().required()) return;
        int communityLimit = Math.min(state.mixedSourceCommunityLimit(),
                Math.max(0, state.capacity() - state.items().size()));
        for (Evidence evidence : ranked) {
            if (state.communitySourceCount() >= communityLimit) break;
            if (isCommunity(evidence)) {
                state.add(evidence, "SOURCE_RETENTION");
            }
        }
    }

    private void retainRequiredQueryGroupCoverage(List<RetrievalQueryGroup> groups, List<Evidence> ranked,
                                                   SelectionState state) {
        for (RetrievalQueryGroup group : groups) {
            if (!group.requiredCoverage()) continue;
            ranked.stream().filter(evidence -> GroupCoverageMatcher.matches(evidence, group)).findFirst()
                    .ifPresent(evidence -> state.add(evidence, "QUERY_GROUP_COVERAGE"));
        }
    }

    private void retainTopicHintCoverage(List<Evidence> ranked, Set<String> topicHints,
                                         SelectionState state) {
        for (String type : topicHints) {
            ranked.stream().filter(this::isPlant).filter(evidence -> type.equals(knowledgeType(evidence)))
                    .findFirst().ifPresent(evidence -> state.add(evidence, "TOPIC_HINT_COVERAGE"));
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
        private final SourcePlan sourcePlan;
        private final int mixedSourceCommunityLimit;
        private final Map<String, Evidence> items = new LinkedHashMap<>();
        private final Map<String, String> reasons = new LinkedHashMap<>();
        private final Set<String> logicalGroups = new LinkedHashSet<>();
        private final Set<String> communitySourceIds = new LinkedHashSet<>();

        private SelectionState(int capacity, SourcePlan sourcePlan, int mixedSourceCommunityLimit) {
            this.capacity = capacity;
            this.sourcePlan = sourcePlan;
            this.mixedSourceCommunityLimit = Math.max(0, mixedSourceCommunityLimit);
        }

        private void add(Evidence evidence, String reason) {
            if (items.containsKey(evidence.id()) || items.size() >= capacity || !canAdd(evidence, reason)) return;
            items.put(evidence.id(), evidence);
            reasons.put(evidence.id(), reason);
            logicalGroups.add(logicalGroup(evidence));
            if (isCommunity(evidence)) communitySourceIds.add(sourceKey(evidence));
        }

        private boolean canAdd(Evidence evidence, String reason) {
            if (logicalGroups.contains(logicalGroup(evidence))) return false;
            if ("QUERY_GROUP_COVERAGE".equals(reason)) return true;
            return !sourcePlan.includeCommunity() || !isCommunity(evidence)
                    || !communitySourceIds.contains(sourceKey(evidence))
                    && communitySourceIds.size() < Math.min(mixedSourceCommunityLimit, Math.max(0, capacity - 1));
        }

        private String logicalGroup(Evidence evidence) {
            return sourceKey(evidence) + "\u0000" + knowledgeType(evidence);
        }

        private String sourceKey(Evidence evidence) {
            return evidence.sourceId() == null || evidence.sourceId().isBlank() ? evidence.id() : evidence.sourceId();
        }

        private int capacity() { return capacity; }
        private int mixedSourceCommunityLimit() { return mixedSourceCommunityLimit; }
        private SourcePlan sourcePlan() { return sourcePlan; }
        private int communitySourceCount() { return communitySourceIds.size(); }
        private Map<String, Evidence> items() { return items; }
        private Map<String, String> reasons() { return reasons; }
    }
}
