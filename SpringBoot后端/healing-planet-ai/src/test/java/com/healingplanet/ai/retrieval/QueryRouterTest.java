package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.QueryIntent;
import com.healingplanet.ai.domain.RagQuery;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QueryRouterTest {
    private final QueryRouter router = new QueryRouter();

    @Test
    void shouldRoutePersonalQuestionToKnowledgeAndState() {
        var result = router.route(RagQuery.of("我的绿萝今天要不要浇水？"));

        assertThat(result.knowledge()).isTrue();
        assertThat(result.state()).isTrue();
        assertThat(result.community()).isFalse();
        assertThat(result.intent()).isEqualTo(QueryIntent.PERSONAL_CARE);
        assertThat(result.stateEvidenceNeed()).isEqualTo(QueryRouter.StateEvidenceNeed.STATE_DECISION);
    }

    @Test
    void shouldRouteCurrentFactToLiveStateOnly() {
        var result = router.route(RagQuery.of("我的绿萝土壤湿度偏低吗？"));

        assertThat(result.knowledge()).isFalse();
        assertThat(result.stateEvidenceNeed()).isEqualTo(QueryRouter.StateEvidenceNeed.STATE_FACT_CURRENT);
    }

    @Test
    void currentWateringDecisionShouldIncludeHistoryWhenItParticipatesInTheAnswer() {
        var result = router.route(RagQuery.of("我这盆绿萝现在需要浇水吗？"));

        assertThat(result.knowledge()).isTrue();
        assertThat(result.stateEvidenceNeed()).isEqualTo(QueryRouter.StateEvidenceNeed.STATE_DECISION_WITH_HISTORY);
    }

    @Test
    void shouldRouteHistoryFactToSensorHistoryOnly() {
        var result = router.route(RagQuery.of("这盆绿萝过去24小时土壤湿度趋势怎样？"));

        assertThat(result.knowledge()).isFalse();
        assertThat(result.stateEvidenceNeed()).isEqualTo(QueryRouter.StateEvidenceNeed.STATE_FACT_HISTORY);
    }

    @Test
    void staleWateringQuestionShouldNotLoadCareGuide() {
        var result = router.route(RagQuery.of("我的绿萝现在需要浇水吗，传感器数据会不会太旧？"));

        assertThat(result.knowledge()).isFalse();
        assertThat(result.stateEvidenceNeed()).isEqualTo(QueryRouter.StateEvidenceNeed.STATE_FRESHNESS);
    }

    @Test
    void explicitIntentShouldTakePriority() {
        var query = new RagQuery("最近大家怎么养绿萝", null, null, null,
                QueryIntent.COMMUNITY_SEARCH, List.of(), Map.of());

        assertThat(router.route(query).intent()).isEqualTo(QueryIntent.COMMUNITY_SEARCH);
    }

    @Test
    void communityWordingShouldNotBeMistakenForRecentPlantState() {
        var result = router.route(RagQuery.of("最近大家怎么养绿萝？"));

        assertThat(result.intent()).isEqualTo(QueryIntent.COMMUNITY_SEARCH);
        assertThat(result.knowledge()).isFalse();
        assertThat(result.community()).isTrue();
        assertThat(result.state()).isFalse();
    }

    @Test
    void generalCareShouldUseFormalKnowledgeOnly() {
        var result = router.route(RagQuery.of("绿萝建议多久浇一次水？"));

        assertThat(result.knowledge()).isTrue();
        assertThat(result.community()).isFalse();
        assertThat(result.state()).isFalse();
        assertThat(result.intent()).isEqualTo(QueryIntent.GENERAL_CARE);
    }

    @Test
    void wateringQuestionWithoutPersonalContextShouldRemainGeneralCare() {
        var result = router.route(RagQuery.of("月球绿萝需要浇水吗？"));

        assertThat(result.intent()).isEqualTo(QueryIntent.GENERAL_CARE);
        assertThat(result.knowledge()).isTrue();
        assertThat(result.state()).isFalse();
    }

    @Test
    void formalAndCommunityWordingShouldUseBothSources() {
        var result = router.route(RagQuery.of("绿萝官方浇水频率是什么？社区经验又怎么做？"));

        assertThat(result.knowledge()).isTrue();
        assertThat(result.community()).isTrue();
        assertThat(result.state()).isFalse();
        assertThat(result.intent()).isEqualTo(QueryIntent.COMMUNITY_SEARCH);
    }

    @Test
    void howToProcessShouldRemainFormalCareQuestion() {
        var result = router.route(RagQuery.of("绿萝出现枯黄叶片时怎么处理？"));

        assertThat(result.knowledge()).isTrue();
        assertThat(result.community()).isFalse();
        assertThat(result.intent()).isEqualTo(QueryIntent.GENERAL_CARE);
    }

    @Test
    void separateCommunityFollowUpShouldUseBothSources() {
        var result = router.route(RagQuery.of("绿萝枯黄叶怎么处理？社区对状态变化有什么观察经验？"));

        assertThat(result.knowledge()).isTrue();
        assertThat(result.community()).isTrue();
    }

    @Test
    void commaSeparatedCommunityFollowUpShouldUseBothSources() {
        var result = router.route(RagQuery.of("绿萝的正式浇水建议，社区经验怎么做？"));

        assertThat(result.knowledge()).isTrue();
        assertThat(result.community()).isTrue();
    }

    @Test
    void abnormalStateQuestionShouldFallbackToKnowledgeWhenStateIsUnavailable() {
        var result = router.route(RagQuery.of("我的绿萝当前状态异常吗？"));

        assertThat(result.intent()).isEqualTo(QueryIntent.PERSONAL_CARE);
        assertThat(result.knowledge()).isTrue();
        assertThat(result.state()).isTrue();
        assertThat(result.stateEvidenceNeed()).isEqualTo(QueryRouter.StateEvidenceNeed.STATE_DECISION);
    }

    @Test
    void shouldClassifyGenericPlantQuestionsAsEntityOptional() {
        var result = router.route(RagQuery.of("适合宿舍养的耐阴植物有哪些？"));

        assertThat(result.domain()).isEqualTo(QueryRouter.QueryDomain.PLANT);
        assertThat(result.entityRequirement()).isEqualTo(QueryRouter.EntityRequirement.OPTIONAL);
    }

    @Test
    void shouldKeepBroadCommunitySearchEntityOptional() {
        var query = new RagQuery("社区最近有哪些比较热门的养护经验？", null, null, null,
                QueryIntent.COMMUNITY_SEARCH, List.of(), Map.of());

        var result = router.route(query);

        assertThat(result.domain()).isEqualTo(QueryRouter.QueryDomain.PLANT);
        assertThat(result.intent()).isEqualTo(QueryIntent.COMMUNITY_SEARCH);
        assertThat(result.entityRequirement()).isEqualTo(QueryRouter.EntityRequirement.OPTIONAL);
    }

    @Test
    void shouldRequireEntityForPlantSpecificCommunitySearch() {
        var query = new RagQuery("社区里大家怎么养绿萝？", null, null, null,
                QueryIntent.COMMUNITY_SEARCH, List.of(), Map.of());

        var result = router.route(query);

        assertThat(result.domain()).isEqualTo(QueryRouter.QueryDomain.PLANT);
        assertThat(result.intent()).isEqualTo(QueryIntent.COMMUNITY_SEARCH);
        assertThat(result.entityRequirement()).isEqualTo(QueryRouter.EntityRequirement.REQUIRED);
    }

    @Test
    void sourcePreferenceShouldNotProvePlantDomain() {
        var query = new RagQuery("网友对量子纠缠有什么经验？", null, null, null,
                QueryIntent.COMMUNITY_SEARCH, List.of(), Map.of());

        var result = router.route(query);

        assertThat(result.domain()).isEqualTo(QueryRouter.QueryDomain.OUT_OF_DOMAIN);
        assertThat(result.intent()).isEqualTo(QueryIntent.COMMUNITY_SEARCH);
        assertThat(result.entityRequirement()).isEqualTo(QueryRouter.EntityRequirement.NONE);
    }

    @Test
    void shouldRequireEntityForSpecificCareQuestions() {
        var result = router.route(RagQuery.of("多久浇一次水？"));

        assertThat(result.domain()).isEqualTo(QueryRouter.QueryDomain.PLANT);
        assertThat(result.entityRequirement()).isEqualTo(QueryRouter.EntityRequirement.REQUIRED);
    }

    @Test
    void shouldRejectQueriesOutsideThePlantDomainBeforeEntityResolution() {
        var result = router.route(RagQuery.of("量子纠缠是什么？"));

        assertThat(result.domain()).isEqualTo(QueryRouter.QueryDomain.OUT_OF_DOMAIN);
        assertThat(result.entityRequirement()).isEqualTo(QueryRouter.EntityRequirement.NONE);
    }
}
