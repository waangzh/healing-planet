package com.healingplanet.ai.service;

import com.healingplanet.ai.config.RagChatOptions;
import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.config.RagRuntimeConfigProvider;
import com.healingplanet.ai.domain.EntityResolutionDiagnostics;
import com.healingplanet.ai.domain.Evidence;
import com.healingplanet.ai.domain.EvidenceType;
import com.healingplanet.ai.domain.QueryIntent;
import com.healingplanet.ai.domain.RagQuery;
import com.healingplanet.ai.evaluation.AnswerabilityEvaluator;
import com.healingplanet.ai.query.QueryAnalysis;
import com.healingplanet.ai.query.RetrievalConstraints;
import com.healingplanet.ai.query.StateNeed;
import com.healingplanet.ai.retrieval.EvidenceRetriever;
import com.healingplanet.ai.retrieval.RetrievalMetrics;
import com.healingplanet.ai.retrieval.RetrievalPlan;
import com.healingplanet.ai.retrieval.RetrievalRequest;
import com.healingplanet.ai.retrieval.RetrievalRequestFactory;
import com.healingplanet.ai.retrieval.RetrievalResult;
import com.healingplanet.ai.retrieval.SourcePlan;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagServiceTest {

    @Test
    void unknownSpecificPlantNeverUsesAnotherPlantsEvidence() {
        EvidenceRetriever retriever = mock(EvidenceRetriever.class);
        RetrievalRequestFactory factory = mock(RetrievalRequestFactory.class);
        RagQuery query = RagQuery.of("火星苔藓适合什么光照？");
        when(factory.create(query)).thenReturn(request(query, Set.of(), 0.9d));
        EntityResolutionDiagnostics unknown = new EntityResolutionDiagnostics("UNKNOWN", "NONE", null,
                List.of(), 0, 0, 0, 0, "no_indexed_entity_candidate");
        when(retriever.retrieveWithDiagnostics(org.mockito.ArgumentMatchers.any(RetrievalRequest.class)))
                .thenReturn(new RetrievalResult(List.of(evidence("other", EvidenceType.CARE_GUIDE, Map.of())), unknown));
        ChatClient chat = mock(ChatClient.class);

        var response = service(retriever, factory, chat).chat(query);

        assertThat(response.answer()).contains("不会使用其它植物的知识代替回答");
        assertThat(response.evidence()).isEmpty();
        verify(chat, never()).prompt();
    }

    @Test
    void entityConflictStopsGeneration() {
        EvidenceRetriever retriever = mock(EvidenceRetriever.class);
        RetrievalRequestFactory factory = mock(RetrievalRequestFactory.class);
        RagQuery query = RagQuery.of("虎尾兰需要什么光照？");
        when(factory.create(query)).thenReturn(request(query, Set.of(), 0.9d));
        EntityResolutionDiagnostics conflict = new EntityResolutionDiagnostics("CONFLICT", "EXPLICIT_ID", "1",
                List.of("1"), 1, 0, 1, 1, "explicit_canonical_plant_id_conflicts_with_query_mention",
                List.of(), List.of(), List.of("虎尾兰"), "CONFLICT");
        when(retriever.retrieveWithDiagnostics(org.mockito.ArgumentMatchers.any(RetrievalRequest.class)))
                .thenReturn(new RetrievalResult(List.of(), conflict));
        ChatClient chat = mock(ChatClient.class);

        var response = service(retriever, factory, chat).chat(query);

        assertThat(response.answer()).contains("已选择的植物", "虎尾兰", "不一致");
        verify(chat, never()).prompt();
    }

    @Test
    void outOfScopeIsDeterminedAfterEmptyRetrieval() {
        EvidenceRetriever retriever = mock(EvidenceRetriever.class);
        RetrievalRequestFactory factory = mock(RetrievalRequestFactory.class);
        RagQuery query = RagQuery.of("量子纠缠是什么？");
        when(factory.create(query)).thenReturn(request(query, Set.of(), 0.02d));
        when(retriever.retrieveWithDiagnostics(org.mockito.ArgumentMatchers.any(RetrievalRequest.class)))
                .thenReturn(new RetrievalResult(List.of(), null));

        var response = service(retriever, factory, mock(ChatClient.class)).chat(query);

        assertThat(response.answer()).contains("不属于当前植物养护知识库的可回答范围");
        verify(retriever).retrieveWithDiagnostics(org.mockito.ArgumentMatchers.any(RetrievalRequest.class));
    }

    @Test
    void outOfScopeIsStillRejectedWhenBroadRetrievalReturnsLowRelevancePlantEvidence() {
        EvidenceRetriever retriever = mock(EvidenceRetriever.class);
        RetrievalRequestFactory factory = mock(RetrievalRequestFactory.class);
        RagQuery query = RagQuery.of("量子纠缠是什么？");
        when(factory.create(query)).thenReturn(request(query, Set.of(), 0.02d));
        Evidence noise = new Evidence("noise", EvidenceType.CARE_GUIDE, "1", "PLANT",
                "绿萝光照指南", "绿萝需要明亮散射光", 0.27d, null, 1d, 0.90d,
                Map.of("knowledgeType", "LIGHT", "canonicalPlantId", "1"), null);
        when(retriever.retrieveWithDiagnostics(org.mockito.ArgumentMatchers.any(RetrievalRequest.class)))
                .thenReturn(new RetrievalResult(List.of(noise), null));
        ChatClient chat = mock(ChatClient.class);

        var response = service(retriever, factory, chat).chat(query);

        assertThat(response.answer()).contains("不属于当前植物养护知识库的可回答范围");
        verify(chat, never()).prompt();
    }

    @Test
    void missingRequiredStateEvidenceReturnsSafeOutcome() {
        EvidenceRetriever retriever = mock(EvidenceRetriever.class);
        RetrievalRequestFactory factory = mock(RetrievalRequestFactory.class);
        RagQuery query = new RagQuery("我的绿萝现在需要浇水吗？", 1L, 1L, null,
                QueryIntent.PERSONAL_CARE, List.of(), Map.of());
        when(factory.create(query)).thenReturn(request(query,
                Set.of(StateNeed.CURRENT, StateNeed.DECISION_SUPPORT), 0.9d));
        when(retriever.retrieveWithDiagnostics(org.mockito.ArgumentMatchers.any(RetrievalRequest.class)))
                .thenReturn(new RetrievalResult(List.of(evidence("guide", EvidenceType.CARE_GUIDE, Map.of())), null));

        var response = service(retriever, factory, mock(ChatClient.class)).chat(query);

        assertThat(response.answer()).contains("暂时无法获取这盆植物的最新状态");
    }

    @Test
    void staleStateBlocksImmediateDecision() {
        EvidenceRetriever retriever = mock(EvidenceRetriever.class);
        RetrievalRequestFactory factory = mock(RetrievalRequestFactory.class);
        RagQuery query = new RagQuery("我的绿萝现在需要浇水吗？", 1L, 1L, null,
                QueryIntent.PERSONAL_CARE, List.of(), Map.of());
        when(factory.create(query)).thenReturn(request(query,
                Set.of(StateNeed.CURRENT, StateNeed.DECISION_SUPPORT), 0.9d));
        when(retriever.retrieveWithDiagnostics(org.mockito.ArgumentMatchers.any(RetrievalRequest.class)))
                .thenReturn(new RetrievalResult(List.of(evidence("live", EvidenceType.LIVE_STATE,
                        Map.of("stale", true, "ageMinutes", 31L))), null));
        ChatClient chat = mock(ChatClient.class);

        var response = service(retriever, factory, chat).chat(query);

        assertThat(response.answer()).contains("距当前 31 分钟", "不能把它作为当前是否需要处理的依据");
        verify(chat, never()).prompt();
    }

    private RagService service(EvidenceRetriever retriever, RetrievalRequestFactory factory, ChatClient chat) {
        return new RagService(retriever, mock(PromptContextBuilder.class), mock(GenerationPromptBuilder.class), chat,
                factory, new AnswerabilityEvaluator(), new RetrievalMetrics(new SimpleMeterRegistry()),
                new RagRuntimeConfigProvider(new RagProperties()), new RagChatOptions());
    }

    private RetrievalRequest request(RagQuery query, Set<StateNeed> needs, double confidence) {
        SourcePlan sourcePlan = new SourcePlan(SourcePlan.SourceRequirement.ALLOWED,
                SourcePlan.SourceRequirement.ALLOWED,
                needs.isEmpty() ? SourcePlan.SourceRequirement.ALLOWED : SourcePlan.SourceRequirement.REQUIRED);
        return new RetrievalRequest(query, new QueryAnalysis(query.intent() == null ? QueryIntent.GENERAL_CARE
                : query.intent(), needs, Set.of(), !needs.isEmpty(), confidence), RetrievalConstraints.defaults(),
                new RetrievalPlan(sourcePlan, true, true, !needs.isEmpty(), needs, Set.of(), query.query()),
                null, query.query());
    }

    private Evidence evidence(String id, EvidenceType type, Map<String, Object> metadata) {
        return new Evidence(id, type, id, "test", id, id, 1d, null, 1d, 1d, metadata, null);
    }
}
