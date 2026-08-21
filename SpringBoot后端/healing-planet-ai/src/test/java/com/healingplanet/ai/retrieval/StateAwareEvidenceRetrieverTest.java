package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.Evidence;
import com.healingplanet.ai.domain.EvidenceType;
import com.healingplanet.ai.domain.QueryIntent;
import com.healingplanet.ai.domain.RagQuery;
import com.healingplanet.ai.config.RagProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StateAwareEvidenceRetrieverTest {

    @Test
    void historyFactShouldReturnHistoryEvidenceOnly() {
        QueryRouter router = mock(QueryRouter.class);
        HybridEvidenceRetriever knowledgeRetriever = mock(HybridEvidenceRetriever.class);
        PlantStateRetriever stateRetriever = mock(PlantStateRetriever.class);
        RagQuery query = RagQuery.of("这盆绿萝过去24小时土壤湿度趋势怎样？");
        when(router.route(query)).thenReturn(new QueryRouter.RoutingDecision(false, false, true,
                QueryIntent.PERSONAL_CARE, QueryRouter.StateEvidenceNeed.STATE_FACT_HISTORY));
        when(stateRetriever.retrieve(query)).thenReturn(List.of(
                evidence("live", EvidenceType.LIVE_STATE), evidence("history", EvidenceType.SENSOR_HISTORY)));

        List<Evidence> result = retriever(router, knowledgeRetriever, stateRetriever).retrieve(query);

        assertThat(result).extracting(Evidence::type).containsExactly(EvidenceType.SENSOR_HISTORY);
    }

    @Test
    void wateringDecisionShouldPassTopicAndKeepCurrentStateOnly() {
        QueryRouter router = mock(QueryRouter.class);
        HybridEvidenceRetriever knowledgeRetriever = mock(HybridEvidenceRetriever.class);
        PlantStateRetriever stateRetriever = mock(PlantStateRetriever.class);
        RagQuery query = RagQuery.of("我的绿萝今天要不要补水？");
        when(router.route(query)).thenReturn(new QueryRouter.RoutingDecision(true, false, true,
                QueryIntent.PERSONAL_CARE, QueryRouter.StateEvidenceNeed.STATE_DECISION));
        when(stateRetriever.retrieve(query)).thenReturn(List.of(
                evidence("live", EvidenceType.LIVE_STATE), evidence("history", EvidenceType.SENSOR_HISTORY)));

        List<Evidence> result = retriever(router, knowledgeRetriever, stateRetriever).retrieve(query);

        ArgumentCaptor<RetrievalRequest> routed = ArgumentCaptor.forClass(RetrievalRequest.class);
        verify(knowledgeRetriever).retrieve(routed.capture());
        assertThat(routed.getValue().requiredKnowledgeTypes()).containsExactly("WATERING");
        assertThat(routed.getValue().query().intent()).isNull();
        assertThat(result).extracting(Evidence::type).containsExactly(EvidenceType.LIVE_STATE);
    }

    @Test
    void multiTopicQuestionShouldKeepAllMatchedKnowledgeTypes() {
        QueryRouter router = mock(QueryRouter.class);
        HybridEvidenceRetriever knowledgeRetriever = mock(HybridEvidenceRetriever.class);
        PlantStateRetriever stateRetriever = mock(PlantStateRetriever.class);
        RagQuery query = RagQuery.of("空气凤梨需要土壤吗？每周如何补水？");
        when(router.route(query)).thenReturn(new QueryRouter.RoutingDecision(true, false, false,
                QueryIntent.GENERAL_CARE, QueryRouter.StateEvidenceNeed.NONE));

        retriever(router, knowledgeRetriever, stateRetriever).retrieve(query);

        ArgumentCaptor<RetrievalRequest> routed = ArgumentCaptor.forClass(RetrievalRequest.class);
        verify(knowledgeRetriever).retrieve(routed.capture());
        assertThat(routed.getValue().requiredKnowledgeTypes())
                .containsExactlyInAnyOrder("WATERING", "GENERAL_CARE");
    }

    @Test
    void shouldExposeRoutingSnapshotWhenEvalTraceIsEnabled() {
        QueryRouter router = mock(QueryRouter.class);
        HybridEvidenceRetriever knowledgeRetriever = mock(HybridEvidenceRetriever.class);
        PlantStateRetriever stateRetriever = mock(PlantStateRetriever.class);
        RagQuery query = RagQuery.of("绿萝需要什么光照？");
        var route = new QueryRouter.RoutingDecision(true, false, false,
                QueryIntent.GENERAL_CARE, QueryRouter.StateEvidenceNeed.NONE);
        when(router.route(query)).thenReturn(route);
        when(knowledgeRetriever.retrieveWithDiagnostics(org.mockito.ArgumentMatchers.any(RetrievalRequest.class)))
                .thenReturn(new RetrievalResult(List.of(), null));
        RagProperties properties = new RagProperties();
        properties.getEval().setRetrievalTraceEnabled(true);
        StateAwareEvidenceRetriever retriever = new StateAwareEvidenceRetriever(router, knowledgeRetriever,
                stateRetriever, new RetrievalMetrics(new SimpleMeterRegistry()), properties);

        var trace = retriever.retrieveWithDiagnostics(query).retrievalTrace();

        assertThat(trace.routing().includeKnowledge()).isTrue();
        assertThat(trace.routing().includeCommunity()).isFalse();
        assertThat(trace.routing().resolvedIntent()).isEqualTo("GENERAL_CARE");
        assertThat(trace.routing().domain()).isEqualTo("PLANT");
        assertThat(trace.routing().entityRequirement()).isEqualTo("REQUIRED");
        assertThat(trace.routing().requiredKnowledgeTypes()).isEqualTo("LIGHT");
        assertThat(trace.stages()).extracting(item -> item.stage()).contains("query_route", "retrieve_total");
    }

    private StateAwareEvidenceRetriever retriever(QueryRouter router, HybridEvidenceRetriever knowledgeRetriever,
                                                   PlantStateRetriever stateRetriever) {
        return new StateAwareEvidenceRetriever(router, knowledgeRetriever, stateRetriever,
                new RetrievalMetrics(new SimpleMeterRegistry()));
    }

    private Evidence evidence(String id, EvidenceType type) {
        return new Evidence(id, type, "source", "test", id, id, 1d, null, 1d, 1d, Map.of(), null);
    }
}
