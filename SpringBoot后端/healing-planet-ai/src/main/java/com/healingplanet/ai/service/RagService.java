package com.healingplanet.ai.service;

import com.healingplanet.ai.config.RagChatOptions;
import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.config.RagRuntimeConfig;
import com.healingplanet.ai.config.RagRuntimeConfigProvider;
import com.healingplanet.ai.config.RagRuntimeSnapshot;
import com.healingplanet.ai.domain.Evidence;
import com.healingplanet.ai.domain.EntityResolutionDiagnostics;
import com.healingplanet.ai.domain.RagQuery;
import com.healingplanet.ai.domain.RagResponse;
import com.healingplanet.ai.domain.RetrievalTrace;
import com.healingplanet.ai.retrieval.EvidenceRetriever;
import com.healingplanet.ai.retrieval.RetrievalResult;
import com.healingplanet.ai.retrieval.RetrievalMetrics;
import com.healingplanet.ai.retrieval.RetrievalRequest;
import com.healingplanet.ai.retrieval.RetrievalRequestFactory;
import com.healingplanet.ai.domain.EvidenceType;
import com.healingplanet.ai.evaluation.Answerability;
import com.healingplanet.ai.evaluation.AnswerabilityEvaluator;
import com.healingplanet.ai.query.StateNeed;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
public class RagService {

    private final EvidenceRetriever retriever;
    private final PromptContextBuilder contextBuilder;
    private final GenerationPromptBuilder promptBuilder;
    private final ChatClient chatClient;
    private final RetrievalRequestFactory requestFactory;
    private final AnswerabilityEvaluator answerabilityEvaluator;
    private final RetrievalMetrics metrics;
    private final RagRuntimeConfigProvider runtimeConfigProvider;
    private final RagChatOptions chatOptions;

    @Autowired
    public RagService(EvidenceRetriever retriever, PromptContextBuilder contextBuilder,
                      GenerationPromptBuilder promptBuilder, ChatClient chatClient,
                      RetrievalRequestFactory requestFactory, AnswerabilityEvaluator answerabilityEvaluator,
                      RetrievalMetrics metrics, RagRuntimeConfigProvider runtimeConfigProvider,
                      RagChatOptions chatOptions) {
        this.retriever = retriever;
        this.contextBuilder = contextBuilder;
        this.promptBuilder = promptBuilder;
        this.chatClient = chatClient;
        this.requestFactory = requestFactory;
        this.answerabilityEvaluator = answerabilityEvaluator;
        this.metrics = metrics;
        this.runtimeConfigProvider = runtimeConfigProvider;
        this.chatOptions = chatOptions;
    }

    public RagResponse chat(RagQuery query) {
        RagRuntimeSnapshot runtime = runtimeConfigProvider.runtimeSnapshot();
        RagRuntimeConfig config = runtime.config();
        RetrievalRequest request = requestFor(query);
        String validation = validateStateQuery(query, request);
        if (validation != null) return new RagResponse(validation, List.of(),
                entityDiagnostics(request), validationTrace(request, validation));
        RetrievalResult retrieval = retriever.retrieveWithDiagnostics(request, runtime);
        List<Evidence> evidence = retrieval.evidence();
        AnswerabilityEvaluator.Assessment assessment = answerabilityEvaluator.evaluate(request, evidence,
                retrieval.entityResolution(), config);
        RetrievalTrace trace = withAnswerability(retrieval.retrievalTrace(), assessment);
        String safeAnswer = safeAnswer(request, retrieval, evidence, assessment);
        if (safeAnswer != null) return new RagResponse(safeAnswer,
                assessment.result() == Answerability.ENTITY_AMBIGUOUS
                        || assessment.result() == Answerability.ENTITY_CONFLICT
                        || assessment.result() == Answerability.ENTITY_UNKNOWN ? List.of() : evidence,
                retrieval.entityResolution(), trace);
        String answer = metrics.time("answer_generation", "llm", () ->
                chatClient.prompt().options(chatOptions.from(config))
                        .system(promptBuilder.build(request, evidence, retrieval.entityResolution()))
                        .user(userPrompt(query.query(), evidence, retrieval.entityResolution())).call().content());
        return new RagResponse(answer, evidence, retrieval.entityResolution(), trace);
    }

    public RagStream stream(RagQuery query) {
        RagRuntimeSnapshot runtime = runtimeConfigProvider.runtimeSnapshot();
        RagRuntimeConfig config = runtime.config();
        RetrievalRequest request = requestFor(query);
        String validation = validateStateQuery(query, request);
        if (validation != null) return new RagStream(List.of(), entityDiagnostics(request),
                validationTrace(request, validation), Flux.just(validation));
        RetrievalResult retrieval = retriever.retrieveWithDiagnostics(request, runtime);
        List<Evidence> evidence = retrieval.evidence();
        AnswerabilityEvaluator.Assessment assessment = answerabilityEvaluator.evaluate(request, evidence,
                retrieval.entityResolution(), config);
        RetrievalTrace trace = withAnswerability(retrieval.retrievalTrace(), assessment);
        String safeAnswer = safeAnswer(request, retrieval, evidence, assessment);
        if (safeAnswer != null) return new RagStream(
                assessment.result() == Answerability.ENTITY_AMBIGUOUS
                        || assessment.result() == Answerability.ENTITY_CONFLICT
                        || assessment.result() == Answerability.ENTITY_UNKNOWN ? List.of() : evidence,
                retrieval.entityResolution(), trace, Flux.just(safeAnswer));
        Flux<String> content = metrics.timeFlux("answer_generation", "llm", () ->
                chatClient.prompt().options(chatOptions.from(config))
                        .system(promptBuilder.build(request, evidence, retrieval.entityResolution()))
                        .user(userPrompt(query.query(), evidence, retrieval.entityResolution())).stream().content());
        return new RagStream(evidence, retrieval.entityResolution(), trace, content);
    }

    public List<Evidence> search(RagQuery query) {
        return search(query, runtimeConfigProvider.runtimeSnapshot());
    }

    List<Evidence> search(RagQuery query, RagRuntimeSnapshot runtime) {
        RetrievalResult result = retriever.retrieveWithDiagnostics(requestFor(query), runtime);
        return entityGuardAnswer(result.entityResolution()) == null ? result.evidence() : List.of();
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

    private String entityGuardAnswer(EntityResolutionDiagnostics resolution) {
        if (resolution == null) return null;
        if (isEntityResolutionDependencyFailure(resolution.rejectionReason())) return null;
        if ("CONFLICT".equals(resolution.resolutionKind())) {
            String mentions = resolution.conflictingMentions().isEmpty() ? "文本中的植物"
                    : String.join("、", resolution.conflictingMentions());
            return "当前已选择的植物与问题中提到的“" + mentions + "”不一致，请确认这次要询问哪株植物。";
        }
        if ("AMBIGUOUS".equals(resolution.resolutionKind())) {
            return "当前植物名称可能对应多个已收录植物，请提供更完整的名称或学名后再试。";
        }
        if ("UNKNOWN".equals(resolution.resolutionKind())) {
            return "当前植物目录中未能可靠识别问题里的具体植物，因此不会使用其它植物的知识代替回答。";
        }
        return null;
    }

    private String outOfScopeAnswer() {
        return "这个问题不属于当前植物养护知识库的可回答范围。";
    }

    private boolean isEntityResolutionDependencyFailure(String reason) {
        return reason != null && (reason.startsWith("llm_disambiguation_")
                || reason.equals("llm_connect_timeout") || reason.equals("llm_read_timeout")
                || reason.equals("llm_connection_failed") || reason.startsWith("llm_http_")
                || reason.equals("llm_invalid_json"));
    }

    private String validateStateQuery(RagQuery query, RetrievalRequest request) {
        if (!request.plan().searchState()) return null;
        if (query.userId() == null) return "个体化状态分析需要 userId，用于校验植物归属。";
        if (query.plantInstanceId() == null) return "个体化状态分析需要 plantInstanceId，请先选择要分析的植物。";
        return null;
    }

    private String staleStateDecisionAnswer(RetrievalRequest request, List<Evidence> evidence) {
        if (!request.stateNeeds().contains(StateNeed.DECISION_SUPPORT)) return null;
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

    private RetrievalRequest requestFor(RagQuery query) {
        return requestFactory.create(query);
    }

    private String safeAnswer(RetrievalRequest request, RetrievalResult retrieval, List<Evidence> evidence,
                              AnswerabilityEvaluator.Assessment assessment) {
        if (retrieval.entityResolution() != null
                && isEntityResolutionDependencyFailure(retrieval.entityResolution().rejectionReason())) {
            return "植物名称识别服务暂时不可用，请稍后重试。";
        }
        return switch (assessment.result()) {
            case ANSWERABLE -> null;
            case ENTITY_CONFLICT, ENTITY_AMBIGUOUS, ENTITY_UNKNOWN -> entityGuardAnswer(retrieval.entityResolution());
            case STATE_UNAVAILABLE -> "暂时无法获取这盆植物的最新状态，因此不能可靠判断当前是否需要处理。请确认设备在线并稍后重试。";
            case STATE_STALE -> staleStateDecisionAnswer(request, evidence);
            case OUT_OF_SCOPE -> outOfScopeAnswer();
            case INSUFFICIENT_EVIDENCE -> "required_state_evidence_forbidden".equals(assessment.reason())
                    ? "按你的要求不读取传感器状态，因此无法判断这盆植物现在是否需要处理；"
                    + "如果需要，我可以另行提供不依赖实时状态的一般养护指南。"
                    : emptyEvidenceAnswer(retrieval);
        };
    }

    private RetrievalTrace withAnswerability(RetrievalTrace trace, AnswerabilityEvaluator.Assessment assessment) {
        return trace == null ? null : trace.withAnswerability(new RetrievalTrace.AnswerabilitySnapshot(
                assessment.result().name(), assessment.reason()));
    }

    private EntityResolutionDiagnostics entityDiagnostics(RetrievalRequest request) {
        return request.entityResolution() == null ? null : request.entityResolution().diagnostics();
    }

    private RetrievalTrace validationTrace(RetrievalRequest request, String validation) {
        String result = validation.contains("userId") ? "REQUIRE_USER_ID" : "REQUIRE_PLANT_INSTANCE";
        return new RetrievalTrace(request.routingSnapshot(), entityDiagnostics(request), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of())
                .withAnswerability(new RetrievalTrace.AnswerabilitySnapshot(result, "explicit_state_identity_required"));
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
