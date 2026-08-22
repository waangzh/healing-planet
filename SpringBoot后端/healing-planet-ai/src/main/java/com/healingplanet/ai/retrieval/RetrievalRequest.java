package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.RagQuery;
import com.healingplanet.ai.domain.RetrievalTrace;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

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
        if (requiredKnowledgeTypes == null) {
            requiredKnowledgeTypes = Set.of();
        } else {
            LinkedHashSet<String> normalizedTypes = requiredKnowledgeTypes.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            requiredKnowledgeTypes = Collections.unmodifiableSet(normalizedTypes);
        }
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
                3,
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
        return KnowledgeTopicClassifier.classify(query);
    }
}
