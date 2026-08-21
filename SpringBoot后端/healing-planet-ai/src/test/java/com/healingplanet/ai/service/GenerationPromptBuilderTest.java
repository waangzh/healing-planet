package com.healingplanet.ai.service;

import com.healingplanet.ai.domain.QueryIntent;
import com.healingplanet.ai.domain.EntityResolutionDiagnostics;
import com.healingplanet.ai.retrieval.RetrievalRequest;
import com.healingplanet.ai.domain.RagQuery;
import com.healingplanet.ai.retrieval.QueryRouter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenerationPromptBuilderTest {
    private final GenerationPromptBuilder builder = new GenerationPromptBuilder();

    @Test
    void generalCareShouldForbidUnrequestedStateDisclaimerAndExtraTopics() {
        String prompt = builder.build(decision(true, false, false, QueryIntent.GENERAL_CARE));

        assertThat(prompt)
                .contains("当前意图：GENERAL_CARE", "不得输出缺少实时状态", "不得引用或扩写问题未涉及的养护主题")
                .contains("证据只支持一个事实时，用一至两句话回答");
    }

    @Test
    void personalCareShouldUseOnlyRelevantStateAndGuideEvidence() {
        String prompt = builder.build(decision(true, false, true, QueryIntent.PERSONAL_CARE));

        assertThat(prompt)
                .contains("当前意图：PERSONAL_CARE", "只使用当前问题相关的传感器项与指南")
                .contains("仅当用户询问数据时效性，或时效性会改变当前结论时");
    }

    @Test
    void staleStateMustOverrideImmediateTreatmentDecision() {
        String prompt = builder.build(decision(true, false, true, QueryIntent.PERSONAL_CARE));

        assertThat(prompt).contains("陈旧性优先于阈值判断", "不得基于该读数输出“现在需要/不需要浇水”");
    }

    @Test
    void personalCareDecisionWithHistoryShouldRequireCurrentValueAndTrendTogether() {
        String prompt = builder.build(new QueryRouter.RoutingDecision(true, false, true, QueryIntent.PERSONAL_CARE,
                QueryRouter.StateEvidenceNeed.STATE_DECISION_WITH_HISTORY));

        assertThat(prompt).contains("必须同时说明当前值和最相关的近24小时趋势");
    }

    @Test
    void personalCareHistoryFactShouldRequireAverageValueWhenAvailable() {
        String prompt = builder.build(new QueryRouter.RoutingDecision(false, false, true, QueryIntent.PERSONAL_CARE,
                QueryRouter.StateEvidenceNeed.STATE_FACT_HISTORY));

        assertThat(prompt).contains("若证据给出了对应窗口平均值，也要一并说明");
    }

    @Test
    void personalCareImmediateWateringShouldForbidFalseRefusalInsideRange() {
        String prompt = builder.build(new QueryRouter.RoutingDecision(true, false, true, QueryIntent.PERSONAL_CARE,
                QueryRouter.StateEvidenceNeed.STATE_DECISION));

        assertThat(prompt).contains("不得仅因缺少上次操作时间而拒答");
    }

    @Test
    void communitySearchShouldKeepExperienceAttribution() {
        String prompt = builder.build(decision(false, true, false, QueryIntent.COMMUNITY_SEARCH));

        assertThat(prompt)
                .contains("当前证据：社区经验", "个人经验")
                .contains("不得把社区经验升级为正式指南、通用结论或确定性建议")
                .doesNotContain("正式指南与社区经验的混合问题");
    }

    @Test
    void mixedQuestionShouldSeparateSourcesAndPreferFormalGuideOnConflict() {
        String prompt = builder.build(decision(true, true, false, QueryIntent.COMMUNITY_SEARCH));

        assertThat(prompt)
                .contains("正式指南与社区经验并存", "分别以“正式指南”和“社区经验”陈述")
                .contains("两类证据冲突时，以正式指南为准")
                .contains("保留这条最关键的具体表现")
                .contains("“正式指南”部分只能使用正式养护证据")
                .contains("不得把社区证据补入或改写成正式指南");
    }

    @Test
    void allIntentsShouldRestrictNumericInference() {
        String prompt = builder.build(decision(true, false, false, QueryIntent.GENERAL_CARE));

        assertThat(prompt)
                .contains("15℃低于指南下限18℃")
                .contains("不得继续推导植物特性、原因、长期影响或处理建议");
    }

    @Test
    void allIntentsShouldForbidStrengtheningCausalityAndScope() {
        String prompt = builder.build(decision(true, false, false, QueryIntent.GENERAL_CARE));

        assertThat(prompt)
                .contains("严格保持证据原本的语义强度")
                .contains("不得把“可能、容易、常见、可、有助于、倾向于”等表述改写成“会、必然、直接导致、一定、适用于所有情况”等更强结论")
                .contains("不得扩大因果和适用范围");
    }

    @Test
    void partialEntityResolutionShouldForbidCrossEntityInference() {
        QueryRouter.RoutingDecision routing = decision(true, false, false, QueryIntent.GENERAL_CARE);
        RetrievalRequest request = RetrievalRequest.from(RagQuery.of("绿萝和常春藤的浇水方法相同吗？"), routing);
        String prompt = builder.build(request, java.util.List.of(),
                new EntityResolutionDiagnostics("PARTIAL", "EXACT_NAME", "1", java.util.List.of("1"),
                        1, 0, 1, 1, "comparison_entity_unresolved", java.util.List.of(), java.util.List.of("常春藤")));

        assertThat(prompt).contains("未收录提及", "无法完成涉及它们的比较", "不得外推给未收录提及");
    }

    private QueryRouter.RoutingDecision decision(boolean knowledge, boolean community, boolean state,
                                                   QueryIntent intent) {
        return new QueryRouter.RoutingDecision(knowledge, community, state, intent);
    }
}
