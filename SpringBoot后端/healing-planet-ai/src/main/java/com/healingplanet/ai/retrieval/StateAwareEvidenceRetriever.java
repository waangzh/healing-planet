package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.config.RagRuntimeConfig;
import com.healingplanet.ai.config.RagRuntimeConfigProvider;
import com.healingplanet.ai.config.RagRuntimeSnapshot;
import com.healingplanet.ai.domain.Evidence;
import com.healingplanet.ai.domain.RetrievalTrace;
import com.healingplanet.ai.query.StateNeed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Primary
@Service
public class StateAwareEvidenceRetriever implements EvidenceRetriever {
    private final HybridEvidenceRetriever knowledgeRetriever;
    private final PlantStateRetriever stateRetriever;
    private final RetrievalMetrics metrics;
    private final RagProperties properties;
    private final RagRuntimeConfigProvider runtimeConfigProvider;

    @Autowired
    public StateAwareEvidenceRetriever(HybridEvidenceRetriever knowledgeRetriever, PlantStateRetriever stateRetriever,
                                       RetrievalMetrics metrics, RagProperties properties,
                                       RagRuntimeConfigProvider runtimeConfigProvider) {
        this.knowledgeRetriever = knowledgeRetriever;
        this.stateRetriever = stateRetriever;
        this.metrics = metrics;
        this.properties = properties;
        this.runtimeConfigProvider = runtimeConfigProvider;
    }

    StateAwareEvidenceRetriever(HybridEvidenceRetriever knowledgeRetriever,
                                PlantStateRetriever stateRetriever, RetrievalMetrics metrics) {
        this(knowledgeRetriever, stateRetriever, metrics, new RagProperties(),
                new RagRuntimeConfigProvider(new RagProperties()));
    }

    StateAwareEvidenceRetriever(HybridEvidenceRetriever knowledgeRetriever,
                                PlantStateRetriever stateRetriever, RetrievalMetrics metrics, RagProperties properties) {
        this(knowledgeRetriever, stateRetriever, metrics, properties, new RagRuntimeConfigProvider(properties));
    }

    @Override
    public List<Evidence> retrieve(RetrievalRequest request) {
        return retrieveWithDiagnostics(request).evidence();
    }

    @Override
    public RetrievalResult retrieveWithDiagnostics(RetrievalRequest request) {
        return retrieveWithDiagnostics(request, runtimeConfigProvider.runtimeSnapshot());
    }

    @Override
    public RetrievalResult retrieveWithDiagnostics(RetrievalRequest request, RagRuntimeSnapshot runtimeSnapshot) {
        RetrievalTraceCollector trace = new RetrievalTraceCollector(
                properties.getEval().isRetrievalTraceEnabled());
        return retrieveWithDiagnostics(request, trace, runtimeSnapshot);
    }

    private RetrievalResult retrieveWithDiagnostics(RetrievalRequest request, RetrievalTraceCollector trace,
                                                    RagRuntimeSnapshot runtimeSnapshot) {
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
        List<Evidence> state = request.plan().searchState()
                ? trace.time("state_search", "state", "all",
                        () -> metrics.time("state_search", "state", () -> stateRetriever.retrieve(request.query())))
                : List.of();
        state = state.stream().filter(item -> stateEvidenceRequired(request.stateNeeds(), item)).toList();
        RetrievalRequest routed = routedRequest(request, state);
        List<Evidence> result = new ArrayList<>(state);
        RetrievalResult knowledge = routed.plan().searchKnowledge() || routed.plan().searchCommunity()
                ? knowledgeRetriever.retrieveWithDiagnostics(routed, runtimeSnapshot) : new RetrievalResult(List.of(), null);
        if (knowledge == null) {
            knowledge = new RetrievalResult(List.of(), null);
        }
        result.addAll(knowledge.evidence());
        metrics.recordCandidates("response", "all", result.size());
        return new RetrievalPayload(new RetrievalResult(result, knowledge.entityResolution(),
                knowledge.retrievalTrace()), routed);
    }

    private boolean stateEvidenceRequired(Set<StateNeed> needs, Evidence evidence) {
        boolean liveRequired = needs.contains(StateNeed.CURRENT) || needs.contains(StateNeed.FRESHNESS)
                || needs.contains(StateNeed.DECISION_SUPPORT);
        return evidence.type() == com.healingplanet.ai.domain.EvidenceType.LIVE_STATE && liveRequired
                || evidence.type() == com.healingplanet.ai.domain.EvidenceType.SENSOR_HISTORY
                && needs.contains(StateNeed.HISTORY);
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
