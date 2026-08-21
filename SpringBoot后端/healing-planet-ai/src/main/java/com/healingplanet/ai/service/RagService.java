package com.healingplanet.ai.service;

import com.healingplanet.ai.domain.Evidence;
import com.healingplanet.ai.domain.EntityResolutionDiagnostics;
import com.healingplanet.ai.domain.RagQuery;
import com.healingplanet.ai.domain.RagResponse;
import com.healingplanet.ai.domain.RetrievalTrace;
import com.healingplanet.ai.retrieval.EvidenceRetriever;
import com.healingplanet.ai.retrieval.QueryRouter;
import com.healingplanet.ai.retrieval.RetrievalResult;
import com.healingplanet.ai.retrieval.RetrievalMetrics;
import com.healingplanet.ai.retrieval.RetrievalRequest;
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
    private final RetrievalMetrics metrics;

    public RagService(EvidenceRetriever retriever, PromptContextBuilder contextBuilder,
                      GenerationPromptBuilder promptBuilder, ChatClient chatClient, QueryRouter queryRouter,
                      RetrievalMetrics metrics) {
        this.retriever = retriever;
        this.contextBuilder = contextBuilder;
        this.promptBuilder = promptBuilder;
        this.chatClient = chatClient;
        this.queryRouter = queryRouter;
        this.metrics = metrics;
    }

    public RagResponse chat(RagQuery query) {
        QueryRouter.RoutingDecision decision = queryRouter.route(query);
        RetrievalRequest request = RetrievalRequest.from(query, decision);
        if (decision.outOfDomain()) {
            return new RagResponse(outOfScopeAnswer(), List.of(), null, routingOnlyTrace(request));
        }
        String validation = validateStateQuery(query, decision);
        if (validation != null) return new RagResponse(validation, List.of());
        RetrievalResult retrieval = retriever.retrieveWithDiagnostics(request);
        List<Evidence> evidence = retrieval.evidence();
        if (missingStateEvidence(decision, evidence)) {
            return new RagResponse("暂时无法获取这盆植物的最新状态，因此不能可靠判断当前是否需要处理。请确认设备在线并稍后重试。", evidence,
                    retrieval.entityResolution(), retrieval.retrievalTrace());
        }
        String staleStateDecision = staleStateDecisionAnswer(decision, evidence);
        if (staleStateDecision != null) {
            return new RagResponse(staleStateDecision, evidence, retrieval.entityResolution(), retrieval.retrievalTrace());
        }
        if (evidence.isEmpty()) return new RagResponse(emptyEvidenceAnswer(retrieval), List.of(),
                retrieval.entityResolution(), retrieval.retrievalTrace());
        String answer = metrics.time("answer_generation", "llm", () ->
                chatClient.prompt().system(promptBuilder.build(request, evidence, retrieval.entityResolution()))
                        .user(userPrompt(query.query(), evidence, retrieval.entityResolution())).call().content());
        return new RagResponse(answer, evidence, retrieval.entityResolution(), retrieval.retrievalTrace());
    }

    public RagStream stream(RagQuery query) {
        QueryRouter.RoutingDecision decision = queryRouter.route(query);
        RetrievalRequest request = RetrievalRequest.from(query, decision);
        if (decision.outOfDomain()) {
            return new RagStream(List.of(), null, routingOnlyTrace(request), Flux.just(outOfScopeAnswer()));
        }
        String validation = validateStateQuery(query, decision);
        if (validation != null) return new RagStream(List.of(), Flux.just(validation));
        RetrievalResult retrieval = retriever.retrieveWithDiagnostics(request);
        List<Evidence> evidence = retrieval.evidence();
        if (missingStateEvidence(decision, evidence)) {
            return new RagStream(evidence, retrieval.entityResolution(), retrieval.retrievalTrace(),
                    Flux.just("暂时无法获取这盆植物的最新状态，因此不能可靠判断当前是否需要处理。请确认设备在线并稍后重试。"));
        }
        String staleStateDecision = staleStateDecisionAnswer(decision, evidence);
        if (staleStateDecision != null) {
            return new RagStream(evidence, retrieval.entityResolution(), retrieval.retrievalTrace(),
                    Flux.just(staleStateDecision));
        }
        if (evidence.isEmpty()) {
            return new RagStream(evidence, retrieval.entityResolution(), retrieval.retrievalTrace(),
                    Flux.just(emptyEvidenceAnswer(retrieval)));
        }
        Flux<String> content = metrics.timeFlux("answer_generation", "llm", () ->
                chatClient.prompt().system(promptBuilder.build(request, evidence, retrieval.entityResolution()))
                        .user(userPrompt(query.query(), evidence, retrieval.entityResolution())).stream().content());
        return new RagStream(evidence, retrieval.entityResolution(), retrieval.retrievalTrace(), content);
    }

    public List<Evidence> search(RagQuery query) {
        return retriever.retrieve(RetrievalRequest.from(query, queryRouter.route(query)));
    }

    String userPrompt(String query, List<Evidence> evidence, EntityResolutionDiagnostics entityResolution) {
        return "用户问题：\n" + query + "\n\n可用证据：\n" + contextBuilder.build(evidence, entityResolution);
    }

    private String emptyEvidenceAnswer(RetrievalResult retrieval) {
        if (retrieval.entityResolution() != null
                && !retrieval.entityResolution().unresolvedMentions().isEmpty()) {
            String unresolved = String.join("、", retrieval.entityResolution().unresolvedMentions());
            if (!retrieval.entityResolution().canonicalPlantIds().isEmpty()) {
                return "当前知识库未收录" + unresolved
                        + "的可靠信息，无法确认它是否与已识别植物相同；同时没有检索到足够的已收录植物证据。";
            }
            return "当前知识库未收录" + unresolved + "的可靠信息，无法完成这个问题。";
        }
        if (retrieval.entityResolution() != null
                && retrieval.entityResolution().rejectionReason() != null
                && isEntityResolutionDependencyFailure(retrieval.entityResolution().rejectionReason())) {
            return "植物名称识别服务暂时不可用，请稍后重试。";
        }
        return "当前知识库中没有足够证据回答这个问题。";
    }

    private String outOfScopeAnswer() {
        return "这个问题不属于当前植物养护知识库的可回答范围。";
    }

    private RetrievalTrace routingOnlyTrace(RetrievalRequest request) {
        return new RetrievalTrace(request.routingSnapshot(), null, List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private boolean isEntityResolutionDependencyFailure(String reason) {
        return reason.startsWith("llm_disambiguation_")
                || reason.equals("llm_connect_timeout") || reason.equals("llm_read_timeout")
                || reason.equals("llm_connection_failed") || reason.startsWith("llm_http_")
                || reason.equals("llm_invalid_json");
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

    private String staleStateDecisionAnswer(QueryRouter.RoutingDecision decision, List<Evidence> evidence) {
        if (decision.intent() != com.healingplanet.ai.domain.QueryIntent.PERSONAL_CARE
                || !requiresImmediateStateDecision(decision.stateEvidenceNeed())) return null;
        for (int index = 0; index < evidence.size(); index++) {
            Evidence item = evidence.get(index);
            if (item.type() != EvidenceType.LIVE_STATE || !Boolean.TRUE.equals(item.metadata().get("stale"))) continue;
            Object ageMinutes = item.metadata().get("ageMinutes");
            String age = ageMinutes instanceof Number value
                    ? "传感器数据距当前 %d 分钟，已超过30分钟".formatted(value.longValue())
                    : "传感器数据已超过30分钟";
            return "%s，不能把它作为当前是否需要处理的依据。请刷新设备读数后再判断。[E%d]"
                    .formatted(age, index + 1);
        }
        return null;
    }

    private boolean requiresImmediateStateDecision(QueryRouter.StateEvidenceNeed need) {
        return need == QueryRouter.StateEvidenceNeed.STATE_DECISION
                || need == QueryRouter.StateEvidenceNeed.STATE_DECISION_WITH_HISTORY
                || need == QueryRouter.StateEvidenceNeed.STATE_FRESHNESS;
    }

    public record RagStream(List<Evidence> evidence, EntityResolutionDiagnostics entityResolution,
                            RetrievalTrace retrievalTrace,
                            Flux<String> content) {
        public RagStream(List<Evidence> evidence, Flux<String> content) {
            this(evidence, null, null, content);
        }

        public RagStream(List<Evidence> evidence, EntityResolutionDiagnostics entityResolution,
                         Flux<String> content) {
            this(evidence, entityResolution, null, content);
        }
    }
}
