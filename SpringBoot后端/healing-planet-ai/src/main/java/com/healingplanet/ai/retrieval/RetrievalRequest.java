package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.RagQuery;
import com.healingplanet.ai.domain.RetrievalTrace;
import com.healingplanet.ai.query.QueryAnalysis;
import com.healingplanet.ai.query.RetrievalConstraints;
import com.healingplanet.ai.query.StateNeed;

import java.util.Objects;
import java.util.Set;

/**
 * Immutable request shared by the complete retrieval chain. Analysis, explicit
 * constraints, entity resolution and retrieval plan are created once upstream.
 */
public record RetrievalRequest(
        RagQuery query,
        QueryAnalysis analysis,
        RetrievalConstraints constraints,
        RetrievalPlan plan,
        PlantEntityResolver.Resolution entityResolution,
        String searchQuery
) {
    public RetrievalRequest {
        query = Objects.requireNonNull(query, "query");
        analysis = Objects.requireNonNull(analysis, "analysis");
        constraints = constraints == null ? RetrievalConstraints.defaults() : constraints;
        plan = Objects.requireNonNull(plan, "plan");
        searchQuery = searchQuery == null ? plan.searchQuery() : searchQuery;
    }

    public SourcePlan sourcePlan() {
        return plan.sourcePlan();
    }

    public Set<StateNeed> stateNeeds() {
        return plan.stateNeeds();
    }

    public Set<String> topicHints() {
        return plan.topicHints();
    }

    public RetrievalRequest withSearchQuery(String value) {
        RetrievalPlan updatedPlan = new RetrievalPlan(plan.sourcePlan(), plan.searchKnowledge(),
                plan.searchCommunity(), plan.searchState(), plan.stateNeeds(), plan.topicHints(), value);
        return new RetrievalRequest(query, analysis, constraints, updatedPlan, entityResolution, value);
    }

    public RetrievalTrace.RoutingSnapshot routingSnapshot() {
        String stateNeedsValue = stateNeeds().isEmpty() ? null
                : stateNeeds().stream().map(Enum::name).sorted().collect(java.util.stream.Collectors.joining(","));
        String topicHintsValue = topicHints().isEmpty() ? null : String.join(",", topicHints());
        return new RetrievalTrace.RoutingSnapshot(
                4,
                plan.searchKnowledge(), plan.searchCommunity(), plan.searchState(),
                query.intent() == null ? null : query.intent().name(), analysis.intentHint().name(),
                analysis.plantDomainConfidence() >= 0.5d ? "PLANT_HINT" : "UNKNOWN_HINT",
                "RESOLVER_OWNED", stateNeedsValue, searchQuery, topicHintsValue,
                sourcePlan().knowledge().name(), sourcePlan().community().name(), sourcePlan().state().name(),
                stateNeedsValue, topicHintsValue, analysis.plantDomainConfidence());
    }
}
