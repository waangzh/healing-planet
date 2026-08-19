package com.healingplanet.ai.service;

import com.healingplanet.ai.domain.Evidence;
import com.healingplanet.ai.domain.EvidenceType;
import com.healingplanet.ai.domain.EntityResolutionDiagnostics;
import com.healingplanet.ai.domain.QueryIntent;
import com.healingplanet.ai.domain.RagQuery;
import com.healingplanet.ai.retrieval.EvidenceRetriever;
import com.healingplanet.ai.retrieval.QueryRouter;
import com.healingplanet.ai.retrieval.RetrievalResult;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.healingplanet.ai.retrieval.RetrievalMetrics;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagServiceTest {

    @Test
    void shouldReturnStateUnavailableBeforeGenericInsufficientKnowledgeWhenOnlyCareGuideIsAvailable() {
        EvidenceRetriever retriever = mock(EvidenceRetriever.class);
        PromptContextBuilder contextBuilder = mock(PromptContextBuilder.class);
        GenerationPromptBuilder promptBuilder = mock(GenerationPromptBuilder.class);
        ChatClient chatClient = mock(ChatClient.class);
        QueryRouter queryRouter = mock(QueryRouter.class);
        RagService service = new RagService(retriever, contextBuilder, promptBuilder, chatClient, queryRouter,
                new RetrievalMetrics(new SimpleMeterRegistry()));
        RagQuery query = new RagQuery("我的绿萝当前状态异常吗？", 1L, 1L, null,
                QueryIntent.PERSONAL_CARE, List.of(), Map.of());
        when(queryRouter.route(query)).thenReturn(new QueryRouter.RoutingDecision(true, false, true,
                QueryIntent.PERSONAL_CARE, QueryRouter.StateEvidenceNeed.STATE_DECISION));
        when(retriever.retrieveWithDiagnostics(query)).thenReturn(new RetrievalResult(List.of(
                new Evidence("guide", EvidenceType.CARE_GUIDE, "g1", "PLANT", "绿萝状态判断",
                        "保持通风并检查黄叶", 1d, null, 1d, 1d, Map.of(), null)
        ), null));

        var response = service.chat(query);

        assertThat(response.answer()).contains("暂时无法获取这盆植物的最新状态");
        assertThat(response.evidence()).hasSize(1);
        assertThat(response.evidence().get(0).type()).isEqualTo(EvidenceType.CARE_GUIDE);
        verify(chatClient, never()).prompt();
    }

    @Test
    void shouldExplainEntityResolutionServiceFailureInsteadOfClaimingMissingKnowledge() {
        EvidenceRetriever retriever = mock(EvidenceRetriever.class);
        PromptContextBuilder contextBuilder = mock(PromptContextBuilder.class);
        GenerationPromptBuilder promptBuilder = mock(GenerationPromptBuilder.class);
        ChatClient chatClient = mock(ChatClient.class);
        QueryRouter queryRouter = mock(QueryRouter.class);
        RagService service = new RagService(retriever, contextBuilder, promptBuilder, chatClient, queryRouter,
                new RetrievalMetrics(new SimpleMeterRegistry()));
        RagQuery query = RagQuery.of("月球绿萝需要浇水吗？");
        when(queryRouter.route(query)).thenReturn(new QueryRouter.RoutingDecision(false, true, false,
                QueryIntent.GENERAL_CARE, QueryRouter.StateEvidenceNeed.NONE));
        when(retriever.retrieveWithDiagnostics(query)).thenReturn(new RetrievalResult(List.of(),
                new EntityResolutionDiagnostics("UNKNOWN", "NONE", null, List.of(),
                        0.8, 0.3, 0.5, 2, "llm_disambiguation_failed")));

        var response = service.chat(query);

        assertThat(response.answer()).isEqualTo("植物名称识别服务暂时不可用，请稍后重试。");
        verify(chatClient, never()).prompt();
    }
}
