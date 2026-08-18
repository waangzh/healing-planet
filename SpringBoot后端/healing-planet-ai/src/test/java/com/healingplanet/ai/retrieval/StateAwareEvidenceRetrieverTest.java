package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.Evidence;
import com.healingplanet.ai.domain.EvidenceType;
import com.healingplanet.ai.domain.QueryIntent;
import com.healingplanet.ai.domain.RagQuery;
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

        ArgumentCaptor<RagQuery> routed = ArgumentCaptor.forClass(RagQuery.class);
        verify(knowledgeRetriever).retrieve(routed.capture());
        assertThat(routed.getValue().context()).containsEntry("requiredKnowledgeType", "WATERING");
        assertThat(result).extracting(Evidence::type).containsExactly(EvidenceType.LIVE_STATE);
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
