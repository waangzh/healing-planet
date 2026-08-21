package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.RagQuery;
import com.healingplanet.ai.domain.RetrievalTrace;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immutable request passed after the single routing decision has been made.
 * RagQuery retains only user-supplied fields; inferred routing state never
 * gets written back into it.
 */
public record RetrievalRequest(
        RagQuery query,
        QueryRouter.RoutingDecision routing,
        SourcePlan sourcePlan,
        String searchQuery,
        Set<String> requiredKnowledgeTypes
) {
    public RetrievalRequest {
        query = Objects.requireNonNull(query, "query");
        routing = Objects.requireNonNull(routing, "routing");
        sourcePlan = Objects.requireNonNullElse(sourcePlan, routing.sourcePlan());
        searchQuery = searchQuery == null ? query.query() : searchQuery;
        requiredKnowledgeTypes = requiredKnowledgeTypes == null ? Set.of()
                : requiredKnowledgeTypes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public static RetrievalRequest from(RagQuery query, QueryRouter.RoutingDecision routing) {
        return new RetrievalRequest(query, routing, routing.sourcePlan(), query.query(),
                requiredKnowledgeTypes(query.query(), routing.sourcePlan()));
    }

    public RetrievalRequest withSearchQuery(String value) {
        return new RetrievalRequest(query, routing, sourcePlan, value, requiredKnowledgeTypes);
    }

    public RetrievalTrace.RoutingSnapshot routingSnapshot() {
        return new RetrievalTrace.RoutingSnapshot(
                sourcePlan.includeKnowledge(), sourcePlan.includeCommunity(), sourcePlan.includeState(),
                query.intent() == null ? null : query.intent().name(),
                routing.intent() == null ? null : routing.intent().name(),
                routing.domain().name(), routing.entityRequirement().name(),
                routing.stateEvidenceNeed() == null ? null : routing.stateEvidenceNeed().name(),
                searchQuery, requiredKnowledgeTypes.isEmpty() ? null : String.join(",", requiredKnowledgeTypes),
                sourcePlan.knowledge().name(), sourcePlan.community().name(), sourcePlan.state().name());
    }

    private static Set<String> requiredKnowledgeTypes(String query, SourcePlan sourcePlan) {
        if (!sourcePlan.includeKnowledge()) return Set.of();
        String text = query == null ? "" : query;
        Set<String> types = new LinkedHashSet<>();
        if (text.contains("光照") || text.contains("阳光") || text.contains("晒")) types.add("LIGHT");
        if (text.contains("浇") || text.contains("补水")) types.add("WATERING");
        if (text.contains("温度") || text.contains("耐冷") || text.contains("耐热")) types.add("TEMPERATURE");
        if (text.contains("湿度")) types.add("HUMIDITY");
        if (text.contains("施肥") || text.contains("肥料")) types.add("FERTILIZING");
        if (text.contains("土壤") || text.contains("盆土") || text.contains("介质")) types.add("GENERAL_CARE");
        return Set.copyOf(types);
    }
}
