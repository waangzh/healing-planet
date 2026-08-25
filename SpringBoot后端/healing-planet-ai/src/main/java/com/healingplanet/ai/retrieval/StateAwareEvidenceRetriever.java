package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.config.RagRuntimeConfig;
import com.healingplanet.ai.config.RagRuntimeConfigProvider;
import com.healingplanet.ai.config.RagRuntimeSnapshot;
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
    private final RagRuntimeConfigProvider runtimeConfigProvider;

    @Autowired
    public StateAwareEvidenceRetriever(QueryRouter router, HybridEvidenceRetriever knowledgeRetriever,
                                       PlantStateRetriever stateRetriever, RetrievalMetrics metrics,
                                       RagProperties properties, RagRuntimeConfigProvider runtimeConfigProvider) {
        this.router = router;
        this.knowledgeRetriever = knowledgeRetriever;
        this.stateRetriever = stateRetriever;
        this.metrics = metrics;
        this.properties = properties;
        this.runtimeConfigProvider = runtimeConfigProvider;
    }

    StateAwareEvidenceRetriever(QueryRouter router, HybridEvidenceRetriever knowledgeRetriever,
                                PlantStateRetriever stateRetriever, RetrievalMetrics metrics) {
        this(router, knowledgeRetriever, stateRetriever, metrics, new RagProperties(),
                new RagRuntimeConfigProvider(new RagProperties()));
    }

    StateAwareEvidenceRetriever(QueryRouter router, HybridEvidenceRetriever knowledgeRetriever,
                                PlantStateRetriever stateRetriever, RetrievalMetrics metrics, RagProperties properties) {
        this(router, knowledgeRetriever, stateRetriever, metrics, properties, new RagRuntimeConfigProvider(properties));
    }

    @Override
    public List<Evidence> retrieve(RagQuery query) {
        return retrieveWithDiagnostics(query).evidence();
    }

    @Override
    public RetrievalResult retrieveWithDiagnostics(RagQuery query) {
        RagRuntimeSnapshot runtimeSnapshot = runtimeConfigProvider.runtimeSnapshot();
        RetrievalTraceCollector trace = new RetrievalTraceCollector(
                properties.getEval().isRetrievalTraceEnabled());
        RetrievalRequest request = trace.time("query_route", "all", "all",
                () -> RetrievalRequest.from(query, router.route(query)));
        return retrieveWithDiagnostics(request, trace, runtimeSnapshot);
    }

    @Override
    public List<Evidence> retrieve(RetrievalRequest request) {
        return retrieveWithDiagnostics(request).evidence();
    }

    @Override
    public RetrievalResult retrieveWithDiagnostics(RetrievalRequest request) {
        RagRuntimeSnapshot runtimeSnapshot = runtimeConfigProvider.runtimeSnapshot();
        RetrievalTraceCollector trace = new RetrievalTraceCollector(
                properties.getEval().isRetrievalTraceEnabled());
        return retrieveWithDiagnostics(request, trace, runtimeSnapshot);
    }

    private RetrievalResult retrieveWithDiagnostics(RetrievalRequest request, RetrievalTraceCollector trace,
                                                    RagRuntimeSnapshot runtimeSnapshot) {
        RagRuntimeConfig config = runtimeSnapshot.config();
        RetrievalPayload payload = trace.time("retrieve_total", "all", "all",
                () -> metrics.time("retrieve_total", "all", () -> retrieveTimed(request, trace, runtimeSnapshot)));
        RetrievalResult result = payload.result();
        RetrievalTrace retrievalTrace = result.retrievalTrace();
        if (properties.getEval().isRetrievalTraceEnabled()) {
            if (retrievalTrace == null) {
                retrievalTrace = new RetrievalTrace(null, result.entityResolution(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
            }
            retrievalTrace = retrievalTrace.withRouting(payload.request().routingSnapshot(),
                    trace.stages());
        }
        return new RetrievalResult(result.evidence(), result.entityResolution(), retrievalTrace);
    }

    private RetrievalPayload retrieveTimed(RetrievalRequest request, RetrievalTraceCollector trace,
                                           RagRuntimeSnapshot runtimeSnapshot) {
        RagRuntimeConfig config = runtimeSnapshot.config();
        if (request.routing().outOfDomain()) {
            return new RetrievalPayload(new RetrievalResult(List.of(), null), request);
        }
        QueryRouter.RoutingDecision route = request.routing();
        List<Evidence> state = route.state()
                ? trace.time("state_search", "state", "all",
                        () -> metrics.time("state_search", "state", () -> stateRetriever.retrieve(request.query())))
                : List.of();
        state = state.stream().filter(item -> stateEvidenceRequired(route.stateEvidenceNeed(), item)).toList();
        RetrievalRequest routed = routedRequest(request, state);
        List<Evidence> result = new ArrayList<>(state);
        RetrievalResult knowledge = route.knowledge() || route.community()
                ? knowledgeRetriever.retrieveWithDiagnostics(routed, runtimeSnapshot) : new RetrievalResult(List.of(), null);
        if (knowledge == null) {
            knowledge = knowledgeRetriever.retrieveWithDiagnostics(routed);
        }
        if (knowledge == null) {
            knowledge = new RetrievalResult(knowledgeRetriever.retrieve(routed), null);
        }
        result.addAll(knowledge.evidence());
        metrics.recordCandidates("response", "all", result.size());
        return new RetrievalPayload(new RetrievalResult(result, knowledge.entityResolution(),
                knowledge.retrievalTrace()), routed);
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

    private RetrievalRequest routedRequest(RetrievalRequest request, List<Evidence> state) {
        String plantName = state.stream().map(Evidence::metadata)
                .map(metadata -> metadata.get("plantName"))
                .filter(String.class::isInstance).map(String.class::cast)
                .filter(value -> !value.isBlank()).findFirst().orElse(null);
        String searchText = plantName == null || request.query().query().contains(plantName)
                ? request.query().query() : plantName + " " + request.query().query();
        return request.withSearchQuery(searchText);
    }

    private record RetrievalPayload(RetrievalResult result, RetrievalRequest request) { }
}
