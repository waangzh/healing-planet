package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.domain.Evidence;
import com.healingplanet.ai.domain.RagQuery;
import com.healingplanet.ai.domain.RetrievalTrace;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Primary
@Service
public class StateAwareEvidenceRetriever implements EvidenceRetriever {
    private final QueryRouter router;
    private final HybridEvidenceRetriever knowledgeRetriever;
    private final PlantStateRetriever stateRetriever;
    private final RetrievalMetrics metrics;
    private final RagProperties properties;

    @Autowired
    public StateAwareEvidenceRetriever(QueryRouter router, HybridEvidenceRetriever knowledgeRetriever,
                                       PlantStateRetriever stateRetriever, RetrievalMetrics metrics,
                                       RagProperties properties) {
        this.router = router;
        this.knowledgeRetriever = knowledgeRetriever;
        this.stateRetriever = stateRetriever;
        this.metrics = metrics;
        this.properties = properties;
    }

    StateAwareEvidenceRetriever(QueryRouter router, HybridEvidenceRetriever knowledgeRetriever,
                                PlantStateRetriever stateRetriever, RetrievalMetrics metrics) {
        this(router, knowledgeRetriever, stateRetriever, metrics, new RagProperties());
    }

    @Override
    public List<Evidence> retrieve(RagQuery query) {
        return retrieveWithDiagnostics(query).evidence();
    }

    @Override
    public RetrievalResult retrieveWithDiagnostics(RagQuery query) {
        RetrievalTraceCollector trace = new RetrievalTraceCollector(
                properties.getEval().isRetrievalTraceEnabled());
        RetrievalPayload payload = metrics.time("retrieve_total", "all", () -> retrieveTimed(query, trace));
        RetrievalResult result = payload.result();
        RetrievalTrace retrievalTrace = result.retrievalTrace();
        if (properties.getEval().isRetrievalTraceEnabled()) {
            if (retrievalTrace == null) {
                retrievalTrace = new RetrievalTrace(null, result.entityResolution(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
            }
            retrievalTrace = retrievalTrace.withRouting(routingSnapshot(payload.route(), payload.routed()),
                    trace.stages());
        }
        return new RetrievalResult(result.evidence(), result.entityResolution(), retrievalTrace);
    }

    private RetrievalPayload retrieveTimed(RagQuery query, RetrievalTraceCollector trace) {
        QueryRouter.RoutingDecision route = trace.time("query_route", "all", "all", () -> router.route(query));
        List<Evidence> state = route.state()
                ? trace.time("state_search", "state", "all",
                        () -> metrics.time("state_search", "state", () -> stateRetriever.retrieve(query)))
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
        return new RetrievalPayload(new RetrievalResult(result, knowledge.entityResolution(),
                knowledge.retrievalTrace()), route, routed);
    }

    private RetrievalTrace.RoutingSnapshot routingSnapshot(QueryRouter.RoutingDecision route, RagQuery routed) {
        Object requiredKnowledgeType = routed.context().get("requiredKnowledgeType");
        if (requiredKnowledgeType == null) {
            Object multi = routed.context().get("requiredKnowledgeTypes");
            if (multi instanceof Iterable<?> values) {
                requiredKnowledgeType = joinValues(values);
            }
        }
        return new RetrievalTrace.RoutingSnapshot(route.knowledge(), route.community(), route.state(),
                route.intent() == null ? null : route.intent().name(),
                route.stateEvidenceNeed() == null ? null : route.stateEvidenceNeed().name(), routed.query(),
                requiredKnowledgeType == null ? null : requiredKnowledgeType.toString());
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
        Set<String> requiredKnowledgeTypes = route.knowledge() ? requiredKnowledgeTypes(query.query()) : Set.of();
        if (requiredKnowledgeTypes.size() == 1) {
            context.put("requiredKnowledgeType", requiredKnowledgeTypes.iterator().next());
        } else if (!requiredKnowledgeTypes.isEmpty()) {
            context.put("requiredKnowledgeTypes", requiredKnowledgeTypes);
        }
        String plantName = state.stream().map(Evidence::metadata)
                .map(metadata -> metadata.get("plantName"))
                .filter(String.class::isInstance).map(String.class::cast)
                .filter(value -> !value.isBlank()).findFirst().orElse(null);
        String searchText = plantName == null || query.query().contains(plantName)
                ? query.query() : plantName + " " + query.query();
        return new RagQuery(searchText, query.userId(), query.plantInstanceId(), query.canonicalPlantId(),
                route.intent(), query.keywords(), context);
    }

    private Set<String> requiredKnowledgeTypes(String query) {
        String text = query == null ? "" : query;
        Map<String, Boolean> topics = Map.of(
                "LIGHT", text.contains("光照") || text.contains("阳光") || text.contains("晒"),
                "WATERING", text.contains("浇") || text.contains("补水"),
                "TEMPERATURE", text.contains("温度") || text.contains("耐冷") || text.contains("耐热"),
                "HUMIDITY", text.contains("湿度"),
                "FERTILIZING", text.contains("施肥") || text.contains("肥料"),
                "GENERAL_CARE", text.contains("土壤") || text.contains("盆土") || text.contains("介质")
        );
        return topics.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private String joinValues(Iterable<?> values) {
        StringBuilder result = new StringBuilder();
        for (Object value : values) {
            if (value == null) continue;
            if (result.length() > 0) result.append(",");
            result.append(value);
        }
        return result.toString();
    }

    private record RetrievalPayload(RetrievalResult result, QueryRouter.RoutingDecision route,
                                    RagQuery routed) { }
}
