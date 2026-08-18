package com.healingplanet.ai.service;

import com.healingplanet.ai.domain.Evidence;
import com.healingplanet.ai.domain.EntityResolutionDiagnostics;
import com.healingplanet.ai.domain.RagQuery;
import com.healingplanet.ai.domain.RagResponse;
import com.healingplanet.ai.retrieval.EvidenceRetriever;
import com.healingplanet.ai.retrieval.QueryRouter;
import com.healingplanet.ai.retrieval.RetrievalResult;
import com.healingplanet.ai.domain.EvidenceType;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
public class RagService {

    private final EvidenceRetriever retriever;
    private final PromptContextBuilder contextBuilder;
    private final GenerationPromptBuilder promptBuilder;
    private final ChatClient chatClient;
    private final QueryRouter queryRouter;

    public RagService(EvidenceRetriever retriever, PromptContextBuilder contextBuilder,
                      GenerationPromptBuilder promptBuilder, ChatClient chatClient, QueryRouter queryRouter) {
        this.retriever = retriever;
        this.contextBuilder = contextBuilder;
        this.promptBuilder = promptBuilder;
        this.chatClient = chatClient;
        this.queryRouter = queryRouter;
    }

    public RagResponse chat(RagQuery query) {
        QueryRouter.RoutingDecision decision = queryRouter.route(query);
        String validation = validateStateQuery(query, decision);
        if (validation != null) return new RagResponse(validation, List.of());
        RetrievalResult retrieval = retriever.retrieveWithDiagnostics(query);
        List<Evidence> evidence = retrieval.evidence();
        if (evidence.isEmpty()) return new RagResponse("当前知识库中没有足够证据回答这个问题。", List.of(),
                retrieval.entityResolution());
        if (missingStateEvidence(decision, evidence)) {
            return new RagResponse("暂时无法获取这盆植物的最新状态，因此不能可靠判断当前是否需要处理。请确认设备在线并稍后重试。", evidence,
                    retrieval.entityResolution());
        }
        String answer = chatClient.prompt().system(promptBuilder.build(decision))
                .user(userPrompt(query.query(), evidence)).call().content();
        return new RagResponse(answer, evidence, retrieval.entityResolution());
    }

    public RagStream stream(RagQuery query) {
        QueryRouter.RoutingDecision decision = queryRouter.route(query);
        String validation = validateStateQuery(query, decision);
        if (validation != null) return new RagStream(List.of(), Flux.just(validation));
        RetrievalResult retrieval = retriever.retrieveWithDiagnostics(query);
        List<Evidence> evidence = retrieval.evidence();
        if (evidence.isEmpty()) {
            return new RagStream(evidence, retrieval.entityResolution(),
                    Flux.just("当前知识库中没有足够证据回答这个问题。"));
        }
        if (missingStateEvidence(decision, evidence)) {
            return new RagStream(evidence, retrieval.entityResolution(), Flux.just("暂时无法获取这盆植物的最新状态，因此不能可靠判断当前是否需要处理。请确认设备在线并稍后重试。"));
        }
        Flux<String> content = chatClient.prompt().system(promptBuilder.build(decision))
                .user(userPrompt(query.query(), evidence)).stream().content();
        return new RagStream(evidence, retrieval.entityResolution(), content);
    }

    public List<Evidence> search(RagQuery query) {
        return retriever.retrieve(query);
    }

    private String userPrompt(String query, List<Evidence> evidence) {
        return "用户问题：\n" + query + "\n\n可用证据：\n" + contextBuilder.build(evidence);
    }

    private String validateStateQuery(RagQuery query, QueryRouter.RoutingDecision decision) {
        if (!decision.state()) return null;
        if (query.userId() == null) return "个体化状态分析需要 userId，用于校验植物归属。";
        if (query.plantInstanceId() == null) return "个体化状态分析需要 plantInstanceId，请先选择要分析的植物。";
        return null;
    }

    private boolean missingStateEvidence(QueryRouter.RoutingDecision decision, List<Evidence> evidence) {
        if (!decision.state()) return false;
        EvidenceType required = decision.stateEvidenceNeed() == QueryRouter.StateEvidenceNeed.STATE_FACT_HISTORY
                ? EvidenceType.SENSOR_HISTORY : EvidenceType.LIVE_STATE;
        return evidence.stream().noneMatch(item -> item.type() == required);
    }

    public record RagStream(List<Evidence> evidence, EntityResolutionDiagnostics entityResolution,
                            Flux<String> content) {
        public RagStream(List<Evidence> evidence, Flux<String> content) {
            this(evidence, null, content);
        }
    }
}
