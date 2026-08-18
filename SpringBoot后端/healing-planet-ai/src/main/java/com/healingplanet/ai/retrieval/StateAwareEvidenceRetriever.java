package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.Evidence;
import com.healingplanet.ai.domain.RagQuery;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Primary
@Service
public class StateAwareEvidenceRetriever implements EvidenceRetriever {
    private final QueryRouter router;
    private final HybridEvidenceRetriever knowledgeRetriever;
    private final PlantStateRetriever stateRetriever;
    private final RetrievalMetrics metrics;

    public StateAwareEvidenceRetriever(QueryRouter router, HybridEvidenceRetriever knowledgeRetriever,
                                       PlantStateRetriever stateRetriever, RetrievalMetrics metrics) {
        this.router = router;
        this.knowledgeRetriever = knowledgeRetriever;
        this.stateRetriever = stateRetriever;
        this.metrics = metrics;
    }

    @Override
    public List<Evidence> retrieve(RagQuery query) {
        return retrieveWithDiagnostics(query).evidence();
    }

    @Override
    public RetrievalResult retrieveWithDiagnostics(RagQuery query) {
        return metrics.time("retrieve_total", "all", () -> retrieveTimed(query));
    }

    private RetrievalResult retrieveTimed(RagQuery query) {
        QueryRouter.RoutingDecision route = router.route(query);
        List<Evidence> state = route.state()
                ? metrics.time("state_search", "state", () -> stateRetriever.retrieve(query))
                : List.of();
        state = state.stream().filter(item -> stateEvidenceRequired(route.stateEvidenceNeed(), item)).toList();
        RagQuery routed = routedQuery(query, route, state);
        List<Evidence> result = new ArrayList<>(state);
        RetrievalResult knowledge = route.knowledge() || route.community()
                ? knowledgeRetriever.retrieveWithDiagnostics(routed) : new RetrievalResult(List.of(), null);
        if (knowledge == null) {
            knowledge = new RetrievalResult(knowledgeRetriever.retrieve(routed), null);
        }
        result.addAll(knowledge.evidence());
        metrics.recordCandidates("response", "all", result.size());
        return new RetrievalResult(result, knowledge.entityResolution());
    }

    private boolean stateEvidenceRequired(QueryRouter.StateEvidenceNeed need, Evidence evidence) {
        return switch (Objects.requireNonNullElse(need, QueryRouter.StateEvidenceNeed.STATE_DECISION)) {
            case STATE_FACT_HISTORY -> evidence.type() == com.healingplanet.ai.domain.EvidenceType.SENSOR_HISTORY;
            case STATE_FACT_CURRENT, STATE_FRESHNESS -> evidence.type() == com.healingplanet.ai.domain.EvidenceType.LIVE_STATE;
            case STATE_DECISION -> evidence.type() == com.healingplanet.ai.domain.EvidenceType.LIVE_STATE;
            case STATE_DECISION_WITH_HISTORY -> evidence.type() == com.healingplanet.ai.domain.EvidenceType.LIVE_STATE
                    || evidence.type() == com.healingplanet.ai.domain.EvidenceType.SENSOR_HISTORY;
            case NONE -> false;
        };
    }

    private RagQuery routedQuery(RagQuery query, QueryRouter.RoutingDecision route, List<Evidence> state) {
        Map<String, Object> context = new HashMap<>(query.context());
        context.put("includePlantKnowledge", route.knowledge());
        context.put("includeCommunity", route.community());
        String requiredKnowledgeType = route.knowledge() ? requiredKnowledgeType(query.query()) : null;
        if (requiredKnowledgeType != null) context.put("requiredKnowledgeType", requiredKnowledgeType);
        String plantName = state.stream().map(Evidence::metadata)
                .map(metadata -> metadata.get("plantName"))
                .filter(String.class::isInstance).map(String.class::cast)
                .filter(value -> !value.isBlank()).findFirst().orElse(null);
        String searchText = plantName == null || query.query().contains(plantName)
                ? query.query() : plantName + " " + query.query();
        return new RagQuery(searchText, query.userId(), query.plantInstanceId(), query.canonicalPlantId(),
                route.intent(), query.keywords(), context);
    }

    private String requiredKnowledgeType(String query) {
        String text = query == null ? "" : query;
        Map<String, Boolean> topics = Map.of(
                "LIGHT", text.contains("光照") || text.contains("阳光") || text.contains("晒"),
                "WATERING", text.contains("浇") || text.contains("补水"),
                "TEMPERATURE", text.contains("温度") || text.contains("耐冷") || text.contains("耐热"),
                "HUMIDITY", text.contains("湿度"),
                "FERTILIZING", text.contains("施肥") || text.contains("肥料")
        );
        List<String> matched = topics.entrySet().stream().filter(Map.Entry::getValue)
                .map(Map.Entry::getKey).toList();
        return matched.size() == 1 ? matched.get(0) : null;
    }
}
