package com.healingplanet.ai.evaluation;

import com.healingplanet.ai.domain.Evidence;
import com.healingplanet.ai.domain.EvidenceType;
import com.healingplanet.ai.domain.QueryIntent;
import com.healingplanet.ai.domain.RagQuery;
import com.healingplanet.ai.query.QueryAnalysis;
import com.healingplanet.ai.query.RetrievalConstraints;
import com.healingplanet.ai.query.StateNeed;
import com.healingplanet.ai.retrieval.RetrievalPlan;
import com.healingplanet.ai.retrieval.RetrievalRequest;
import com.healingplanet.ai.retrieval.SourcePlan;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerabilityEvaluatorTest {
    private final AnswerabilityEvaluator evaluator = new AnswerabilityEvaluator();

    @Test
    void lowPlantHintIsOutOfScopeAfterEmptyRetrieval() {
        var result = evaluator.evaluate(request("量子纠缠是什么？", Set.of(), 0.02d), List.of(), null);

        assertThat(result.result()).isEqualTo(Answerability.OUT_OF_SCOPE);
        assertThat(result.reason()).contains("without_strong_relevant_evidence");
    }

    @Test
    void lowScoringDenseHitsDoNotMakeAnOutOfScopeQuestionAnswerable() {
        Evidence unrelatedPlantHit = new Evidence("noise", EvidenceType.CARE_GUIDE, "1", "PLANT",
                "绿萝光照指南", "绿萝需要明亮散射光", 0.27d, null, 1d, 0.90d,
                Map.of("knowledgeType", "LIGHT", "canonicalPlantId", "1"), null);

        var result = evaluator.evaluate(request("量子纠缠是什么？", Set.of(), 0.02d),
                List.of(unrelatedPlantHit), null);

        assertThat(result.result()).isEqualTo(Answerability.OUT_OF_SCOPE);
        assertThat(result.reason()).contains("without_strong_relevant_evidence");
    }

    @Test
    void strongRetrievedEvidenceCanRecoverAWeakDomainHint() {
        Evidence strongHit = new Evidence("strong", EvidenceType.CARE_GUIDE, "1", "PLANT",
                "植物概念", "相关内容", 0.72d, null, 1d, 0.70d, Map.of(), null);

        assertThat(evaluator.evaluate(request("自然语言新变体", Set.of(), 0.25d),
                List.of(strongHit), null).result()).isEqualTo(Answerability.ANSWERABLE);
    }

    @Test
    void requiredCommunitySourceMustBeSatisfiedBySelectedEvidence() {
        var base = request("花友怎么养绿萝？", Set.of(), 0.9d);
        SourcePlan requiredCommunity = new SourcePlan(SourcePlan.SourceRequirement.ALLOWED,
                SourcePlan.SourceRequirement.REQUIRED, SourcePlan.SourceRequirement.ALLOWED);
        RetrievalRequest request = new RetrievalRequest(base.query(), base.analysis(), base.constraints(),
                new RetrievalPlan(requiredCommunity, true, true, false, Set.of(), Set.of(), base.searchQuery()),
                null, base.searchQuery());

        var result = evaluator.evaluate(request,
                List.of(evidence("guide", EvidenceType.CARE_GUIDE, Map.of())), null);

        assertThat(result.result()).isEqualTo(Answerability.INSUFFICIENT_EVIDENCE);
        assertThat(result.reason()).isEqualTo("required_community_evidence_missing");
    }

    @Test
    void compositeStateNeedRequiresBothCurrentAndHistoryEvidence() {
        var request = request("绿萝现在湿度多少，过去一天变化怎么样，需要浇水吗？",
                EnumSet.of(StateNeed.CURRENT, StateNeed.HISTORY, StateNeed.DECISION_SUPPORT), 0.9d);

        assertThat(evaluator.evaluate(request, List.of(evidence("live", EvidenceType.LIVE_STATE, Map.of())), null).result())
                .isEqualTo(Answerability.STATE_UNAVAILABLE);
        assertThat(evaluator.evaluate(request, List.of(evidence("live", EvidenceType.LIVE_STATE, Map.of()),
                evidence("history", EvidenceType.SENSOR_HISTORY, Map.of())), null).result())
                .isEqualTo(Answerability.ANSWERABLE);
    }

    @Test
    void historyOnlyNeedDoesNotRequireLiveState() {
        var request = request("这盆绿萝过去24小时土壤湿度趋势怎样？",
                EnumSet.of(StateNeed.HISTORY), 0.9d);

        assertThat(evaluator.evaluate(request,
                List.of(evidence("history", EvidenceType.SENSOR_HISTORY, Map.of())), null).result())
                .isEqualTo(Answerability.ANSWERABLE);
    }

    @Test
    void staleCurrentStateBlocksImmediateDecisionEvenWhenOtherEvidenceExists() {
        var request = request("虎尾兰现在需要浇水吗？", EnumSet.of(StateNeed.CURRENT,
                StateNeed.DECISION_SUPPORT), 0.9d);
        var result = evaluator.evaluate(request, List.of(evidence("live", EvidenceType.LIVE_STATE,
                Map.of("stale", true)), evidence("guide", EvidenceType.CARE_GUIDE, Map.of())), null);

        assertThat(result.result()).isEqualTo(Answerability.STATE_STALE);
    }

    private RetrievalRequest request(String text, Set<StateNeed> needs, double confidence) {
        RagQuery query = RagQuery.of(text);
        SourcePlan sourcePlan = new SourcePlan(SourcePlan.SourceRequirement.ALLOWED,
                SourcePlan.SourceRequirement.ALLOWED, SourcePlan.SourceRequirement.REQUIRED);
        return new RetrievalRequest(query, new QueryAnalysis(QueryIntent.GENERAL_CARE, needs, Set.of(), false,
                confidence), RetrievalConstraints.defaults(), new RetrievalPlan(sourcePlan, true, true, true,
                needs, Set.of(), text), null, text);
    }

    private Evidence evidence(String id, EvidenceType type, Map<String, Object> metadata) {
        return new Evidence(id, type, id, "test", id, id, 1d, null, 1d, 1d, metadata, null);
    }
}
