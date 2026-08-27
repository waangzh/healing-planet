package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.domain.Evidence;
import com.healingplanet.ai.domain.EvidenceType;
import com.healingplanet.ai.domain.QueryIntent;
import com.healingplanet.ai.domain.RagQuery;
import com.healingplanet.ai.query.QueryAnalysis;
import com.healingplanet.ai.query.RetrievalConstraints;
import com.healingplanet.ai.query.StateNeed;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StateAwareEvidenceRetrieverTest {

    @Test
    void historyNeedReturnsHistoryEvidenceOnly() {
        HybridEvidenceRetriever knowledge = mock(HybridEvidenceRetriever.class);
        PlantStateRetriever state = mock(PlantStateRetriever.class);
        RetrievalRequest request = request("这盆绿萝过去24小时土壤湿度趋势怎样？",
                SourcePlan.SourceRequirement.FORBIDDEN, Set.of(StateNeed.HISTORY));
        when(state.retrieve(request.query())).thenReturn(List.of(evidence("live", EvidenceType.LIVE_STATE),
                evidence("history", EvidenceType.SENSOR_HISTORY)));

        List<Evidence> result = retriever(knowledge, state).retrieve(request);

        assertThat(result).extracting(Evidence::type).containsExactly(EvidenceType.SENSOR_HISTORY);
    }

    @Test
    void decisionPassesSingleRequestWithTopicHintsToKnowledgeRetriever() {
        HybridEvidenceRetriever knowledge = mock(HybridEvidenceRetriever.class);
        PlantStateRetriever state = mock(PlantStateRetriever.class);
        RetrievalRequest request = request("我的绿萝今天要不要补水？", SourcePlan.SourceRequirement.ALLOWED,
                Set.of(StateNeed.CURRENT, StateNeed.DECISION_SUPPORT));
        when(state.retrieve(request.query())).thenReturn(List.of(evidence("live", EvidenceType.LIVE_STATE),
                evidence("history", EvidenceType.SENSOR_HISTORY)));
        when(knowledge.retrieve(org.mockito.ArgumentMatchers.any(RetrievalRequest.class))).thenReturn(List.of());

        List<Evidence> result = retriever(knowledge, state).retrieve(request);

        ArgumentCaptor<RetrievalRequest> captured = ArgumentCaptor.forClass(RetrievalRequest.class);
        verify(knowledge).retrieve(captured.capture());
        assertThat(captured.getValue().analysis()).isSameAs(request.analysis());
        assertThat(captured.getValue().plan()).isEqualTo(request.plan());
        assertThat(captured.getValue().topicHints()).contains("WATERING");
        assertThat(result).extracting(Evidence::type).containsExactly(EvidenceType.LIVE_STATE);
    }

    @Test
    void traceExposesTheAlreadyComputedPlanWithoutRoutingStage() {
        HybridEvidenceRetriever knowledge = mock(HybridEvidenceRetriever.class);
        PlantStateRetriever state = mock(PlantStateRetriever.class);
        RagProperties properties = new RagProperties();
        properties.getEval().setRetrievalTraceEnabled(true);
        RetrievalRequest request = request("绿萝需要什么光照？", SourcePlan.SourceRequirement.ALLOWED, Set.of());
        when(knowledge.retrieveWithDiagnostics(org.mockito.ArgumentMatchers.any(RetrievalRequest.class)))
                .thenReturn(new RetrievalResult(List.of(), null));
        StateAwareEvidenceRetriever retriever = new StateAwareEvidenceRetriever(knowledge, state,
                new RetrievalMetrics(new SimpleMeterRegistry()), properties);

        var trace = retriever.retrieveWithDiagnostics(request).retrievalTrace();

        assertThat(trace.routing().schemaVersion()).isEqualTo(4);
        assertThat(trace.routing().includeKnowledge()).isTrue();
        assertThat(trace.routing().stateNeeds()).isNull();
        assertThat(trace.stages()).extracting(item -> item.stage()).contains("retrieve_total");
        assertThat(trace.stages()).extracting(item -> item.stage()).doesNotContain("query_route");
    }

    private StateAwareEvidenceRetriever retriever(HybridEvidenceRetriever knowledge, PlantStateRetriever state) {
        return new StateAwareEvidenceRetriever(knowledge, state, new RetrievalMetrics(new SimpleMeterRegistry()));
    }

    private RetrievalRequest request(String text, SourcePlan.SourceRequirement knowledge, Set<StateNeed> needs) {
        RagQuery query = RagQuery.of(text);
        SourcePlan plan = new SourcePlan(knowledge, SourcePlan.SourceRequirement.FORBIDDEN,
                needs.isEmpty() ? SourcePlan.SourceRequirement.ALLOWED : SourcePlan.SourceRequirement.REQUIRED);
        return new RetrievalRequest(query, new QueryAnalysis(QueryIntent.GENERAL_CARE, needs,
                KnowledgeTopicClassifier.classify(text), true, 0.9d), RetrievalConstraints.defaults(),
                new RetrievalPlan(plan, plan.includeKnowledge(), false, !needs.isEmpty(), needs,
                        KnowledgeTopicClassifier.classify(text), text), null, text);
    }

    private Evidence evidence(String id, EvidenceType type) {
        return new Evidence(id, type, "source", "test", id, id, 1d, null, 1d, 1d, Map.of(), null);
    }
}
