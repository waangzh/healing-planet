package com.healingplanet.ai.service;

import com.healingplanet.ai.domain.EntityResolutionDiagnostics;
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

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GenerationPromptBuilderTest {
    private final GenerationPromptBuilder builder = new GenerationPromptBuilder();

    @Test
    void formalAndCommunityPoliciesFollowActualEvidence() {
        String prompt = builder.build(request(Set.of()), List.of(guide(), community()));

        assertThat(prompt).contains("正式指南与社区经验并存", "分别以“正式指南”和“社区经验”陈述",
                "两类证据冲突时，以正式指南为准");
    }

    @Test
    void statePolicyRequiresCurrentValueAndHistoryForCompositeNeed() {
        String prompt = builder.build(request(Set.of(StateNeed.CURRENT, StateNeed.HISTORY,
                StateNeed.DECISION_SUPPORT)), List.of(live(), history()));

        assertThat(prompt).contains("实时状态或传感器历史", "必须同时说明当前值和最相关的近24小时趋势");
    }

    @Test
    void generalCareDoesNotAddStateDisclaimerWithoutStateEvidence() {
        String prompt = builder.build(request(Set.of()), List.of(guide()));

        assertThat(prompt).contains("当前意图：GENERAL_CARE", "不得输出缺少实时状态");
    }

    @Test
    void entityPoliciesRemainEvidenceScoped() {
        String partial = builder.build(request(Set.of()), List.of(guide()),
                new EntityResolutionDiagnostics("PARTIAL", "EXACT_NAME", "1", List.of("1"), 1, 0, 1, 1,
                        "comparison_entity_unresolved", List.of(), List.of("常春藤")));
        String conflict = builder.build(request(Set.of()), List.of(),
                new EntityResolutionDiagnostics("CONFLICT", "EXPLICIT_ID", "1", List.of("1"), 1, 0, 1, 1,
                        "conflict", List.of(), List.of(), List.of("虎尾兰"), "CONFLICT"));

        assertThat(partial).contains("未收录提及", "不得外推给未收录提及");
        assertThat(conflict).contains("文本植物与已选择植物冲突", "目录中已知植物");
    }

    private RetrievalRequest request(Set<StateNeed> needs) {
        RagQuery query = RagQuery.of("绿萝怎么养？");
        SourcePlan sourcePlan = new SourcePlan(SourcePlan.SourceRequirement.ALLOWED,
                SourcePlan.SourceRequirement.ALLOWED,
                needs.isEmpty() ? SourcePlan.SourceRequirement.ALLOWED : SourcePlan.SourceRequirement.REQUIRED);
        return new RetrievalRequest(query, new QueryAnalysis(QueryIntent.GENERAL_CARE, needs, Set.of(),
                !needs.isEmpty(), 0.9d), RetrievalConstraints.defaults(), new RetrievalPlan(sourcePlan, true, true,
                !needs.isEmpty(), needs, Set.of(), query.query()), null, query.query());
    }

    private Evidence guide() { return evidence("guide", EvidenceType.CARE_GUIDE); }
    private Evidence community() { return evidence("post", EvidenceType.COMMUNITY_POST); }
    private Evidence live() { return evidence("live", EvidenceType.LIVE_STATE); }
    private Evidence history() { return evidence("history", EvidenceType.SENSOR_HISTORY); }

    private Evidence evidence(String id, EvidenceType type) {
        return new Evidence(id, type, id, type.name(), id, id, 1d, null, 1d, 1d, Map.of(), null);
    }
}
