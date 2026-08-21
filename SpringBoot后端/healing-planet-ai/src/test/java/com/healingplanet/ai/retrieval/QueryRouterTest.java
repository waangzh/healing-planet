package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.QueryIntent;
import com.healingplanet.ai.domain.RagQuery;
import com.healingplanet.ai.ingestion.KnowledgeRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        assertThat(result.sourcePlan().state()).isEqualTo(SourcePlan.SourceRequirement.REQUIRED);
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
    void wateringDecisionWithHistoryTermsShouldKeepDecisionAndHistoryTogether() {
        var result = router.route(RagQuery.of(
                "我这盆绿萝现在需要浇水吗？请完整说明当前读数、过去24小时趋势和正式指南。"));

        assertThat(result.knowledge()).isTrue();
        assertThat(result.state()).isTrue();
        assertThat(result.stateEvidenceNeed()).isEqualTo(QueryRouter.StateEvidenceNeed.STATE_DECISION_WITH_HISTORY);
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

        assertThat(router.route(query).intent()).isEqualTo(QueryIntent.GENERAL_CARE);
    }

    @Test
    void communityWordingShouldNotBeMistakenForRecentPlantState() {
        var result = router.route(RagQuery.of("最近大家怎么养绿萝？"));

        assertThat(result.intent()).isEqualTo(QueryIntent.GENERAL_CARE);
        assertThat(result.knowledge()).isFalse();
        assertThat(result.community()).isTrue();
        assertThat(result.state()).isFalse();
    }

    @Test
    void generalCareShouldUseFormalKnowledgeWithCommunityFallback() {
        var result = router.route(RagQuery.of("绿萝建议多久浇一次水？"));

        assertThat(result.knowledge()).isTrue();
        assertThat(result.community()).isTrue();
        assertThat(result.state()).isFalse();
        assertThat(result.intent()).isEqualTo(QueryIntent.GENERAL_CARE);
        assertThat(result.sourcePlan().knowledge()).isEqualTo(SourcePlan.SourceRequirement.OPTIONAL);
        assertThat(result.sourcePlan().community()).isEqualTo(SourcePlan.SourceRequirement.OPTIONAL);
    }

    @Test
    void implicitExperienceCasesShouldRemainGeneralCareWithCommunityFallback() {
        for (String query : List.of(
                "空气凤梨长期放在潮湿又闷的地方养可以吗？",
                "芦荟长期在室内养，能直接搬到烈日下吗？",
                "芦荟短期偏干和长期积水，哪个更容易恢复？",
                "虎尾兰天气凉时浇水该怎么收着点？")) {
            var result = router.route(RagQuery.of(query));

            assertThat(result.intent()).as(query).isEqualTo(QueryIntent.GENERAL_CARE);
            assertThat(result.sourcePlan().knowledge()).as(query).isEqualTo(SourcePlan.SourceRequirement.OPTIONAL);
            assertThat(result.sourcePlan().community()).as(query).isEqualTo(SourcePlan.SourceRequirement.OPTIONAL);
        }
    }

    @Test
    void sourcePlanShouldSplitFormalAndCommunityClausesWithoutChangingIntent() {
        var result = router.route(RagQuery.of("白掌的湿度要求和花友的光照摆放经验分别是什么？"));

        assertThat(result.intent()).isEqualTo(QueryIntent.GENERAL_CARE);
        assertThat(result.sourcePlan().knowledge()).isEqualTo(SourcePlan.SourceRequirement.REQUIRED);
        assertThat(result.sourcePlan().community()).isEqualTo(SourcePlan.SourceRequirement.REQUIRED);
    }

    @Test
    void topicFrameShouldNotTurnACommunityOnlyClauseIntoFormalRetrieval() {
        var result = router.route(RagQuery.of("白掌日常养护时，花友如何平衡湿润与积水？"));

        assertThat(result.intent()).isEqualTo(QueryIntent.GENERAL_CARE);
        assertThat(result.sourcePlan().knowledge()).isEqualTo(SourcePlan.SourceRequirement.OFF);
        assertThat(result.sourcePlan().community()).isEqualTo(SourcePlan.SourceRequirement.REQUIRED);
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
        assertThat(result.intent()).isEqualTo(QueryIntent.GENERAL_CARE);
    }

    @Test
    void howToProcessShouldRemainFormalCareQuestion() {
        var result = router.route(RagQuery.of("绿萝出现枯黄叶片时怎么处理？"));

        assertThat(result.knowledge()).isTrue();
        assertThat(result.community()).isTrue();
        assertThat(result.sourcePlan().community()).isEqualTo(SourcePlan.SourceRequirement.OPTIONAL);
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
    void mixedSourceQuestionShouldUseClauseLevelSourcePlanning() {
        var result = router.route(RagQuery.of("绿萝怎么养？大家有什么经验？"));

        assertThat(result.intent()).isEqualTo(QueryIntent.GENERAL_CARE);
        assertThat(result.knowledge()).isTrue();
        assertThat(result.community()).isTrue();
        assertThat(result.state()).isFalse();
    }

    @Test
    void commaSeparatedMixedSourceQuestionShouldUseBothSources() {
        var result = router.route(RagQuery.of("绿萝怎么养，大家有什么经验？"));

        assertThat(result.intent()).isEqualTo(QueryIntent.GENERAL_CARE);
        assertThat(result.knowledge()).isTrue();
        assertThat(result.community()).isTrue();
    }

    @Test
    void variedCareAndCommunityClausesShouldUseBothSources() {
        for (String query : List.of(
                "绿萝多久浇一次水，大家一般怎么浇？",
                "白掌黄叶怎么处理，有没有网友遇到过？",
                "龟背竹湿度要求是多少？大家会开加湿器吗？",
                "虎尾兰能晒太阳吗？花友一般放哪里？")) {
            var result = router.route(RagQuery.of(query));

            assertThat(result.knowledge()).as(query).isTrue();
            assertThat(result.community()).as(query).isTrue();
        }
    }

    @Test
    void pureCommunityQuestionShouldNotAutoUpgradeToFormalKnowledge() {
        var result = router.route(RagQuery.of("大家养绿萝有什么经验？"));

        assertThat(result.intent()).isEqualTo(QueryIntent.GENERAL_CARE);
        assertThat(result.knowledge()).isFalse();
        assertThat(result.community()).isTrue();
    }

    @Test
    void negatedCommunityPhraseShouldNotSwitchToCommunityIntent() {
        var result = router.route(RagQuery.of("芦荟多久浇一次水？不要混入虎尾兰的耐旱经验。"));

        assertThat(result.intent()).isEqualTo(QueryIntent.GENERAL_CARE);
        assertThat(result.knowledge()).isTrue();
        assertThat(result.community()).isFalse();
    }

    @Test
    void negatedCommunityFollowUpShouldKeepKnowledgeOnly() {
        var result = router.route(RagQuery.of("绿萝怎么养？不要参考网友经验。"));

        assertThat(result.intent()).isEqualTo(QueryIntent.GENERAL_CARE);
        assertThat(result.knowledge()).isTrue();
        assertThat(result.community()).isFalse();
    }

    @Test
    void communityOnlyPreferenceShouldDisableFormalKnowledge() {
        var result = router.route(RagQuery.of("只想看看花友经验，不需要官方指南。"));

        assertThat(result.intent()).isEqualTo(QueryIntent.GENERAL_CARE);
        assertThat(result.knowledge()).isFalse();
        assertThat(result.community()).isTrue();
    }

    @Test
    void communityOnlyPreferenceShouldOverrideImplicitCareRequest() {
        var result = router.route(RagQuery.of("绿萝怎么养？只看花友经验。"));

        assertThat(result.knowledge()).isFalse();
        assertThat(result.community()).isTrue();
    }

    @Test
    void knowledgeOnlyPreferenceShouldOverrideCommunityRequest() {
        var result = router.route(RagQuery.of("绿萝怎么养？只看官方指南，不要网友经验。"));

        assertThat(result.knowledge()).isTrue();
        assertThat(result.community()).isFalse();
    }

    @Test
    void explicitCommunityIntentShouldStillAllowMixedSources() {
        var query = new RagQuery("绿萝怎么养？大家有什么经验？", null, null, null,
                QueryIntent.COMMUNITY_SEARCH, List.of(), Map.of());

        var result = router.route(query);

        assertThat(result.intent()).isEqualTo(QueryIntent.GENERAL_CARE);
        assertThat(result.knowledge()).isTrue();
        assertThat(result.community()).isTrue();
    }

    @Test
    void explicitCommunityIntentShouldRespectCommunityExclusion() {
        var query = new RagQuery("绿萝怎么养？不要参考网友经验。", null, null, null,
                QueryIntent.COMMUNITY_SEARCH, List.of(), Map.of());

        var result = router.route(query);

        assertThat(result.knowledge()).isTrue();
        assertThat(result.community()).isFalse();
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
        assertThat(result.intent()).isEqualTo(QueryIntent.GENERAL_CARE);
        assertThat(result.entityRequirement()).isEqualTo(QueryRouter.EntityRequirement.OPTIONAL);
    }

    @Test
    void shouldRequireEntityForPlantSpecificCommunitySearch() {
        var query = new RagQuery("社区里大家怎么养绿萝？", null, null, null,
                QueryIntent.COMMUNITY_SEARCH, List.of(), Map.of());

        var result = router.route(query);

        assertThat(result.domain()).isEqualTo(QueryRouter.QueryDomain.PLANT);
        assertThat(result.intent()).isEqualTo(QueryIntent.GENERAL_CARE);
        assertThat(result.entityRequirement()).isEqualTo(QueryRouter.EntityRequirement.REQUIRED);
    }

    @Test
    void sourcePreferenceShouldNotProvePlantDomain() {
        var query = new RagQuery("网友对量子纠缠有什么经验？", null, null, null,
                QueryIntent.COMMUNITY_SEARCH, List.of(), Map.of());

        var result = router.route(query);

        assertThat(result.domain()).isEqualTo(QueryRouter.QueryDomain.OUT_OF_DOMAIN);
        assertThat(result.intent()).isEqualTo(QueryIntent.GENERAL_CARE);
        assertThat(result.entityRequirement()).isEqualTo(QueryRouter.EntityRequirement.NONE);
    }

    @Test
    void shouldRequireEntityForSpecificCareQuestions() {
        var result = router.route(RagQuery.of("多久浇一次水？"));

        assertThat(result.domain()).isEqualTo(QueryRouter.QueryDomain.PLANT);
        assertThat(result.entityRequirement()).isEqualTo(QueryRouter.EntityRequirement.REQUIRED);
    }

    @Test
    void currentGuideQuestionShouldStayInFormalKnowledgeRoute() {
        var result = router.route(RagQuery.of(
                "红掌适宜温度是多少？请只给当前指南范围，不要补充未给出的耐寒结论。"));

        assertThat(result.intent()).isEqualTo(QueryIntent.GENERAL_CARE);
        assertThat(result.knowledge()).isTrue();
        assertThat(result.state()).isFalse();
    }

    @Test
    void shouldRejectQueriesOutsideThePlantDomainBeforeEntityResolution() {
        var result = router.route(RagQuery.of("量子纠缠是什么？"));

        assertThat(result.domain()).isEqualTo(QueryRouter.QueryDomain.OUT_OF_DOMAIN);
        assertThat(result.entityRequirement()).isEqualTo(QueryRouter.EntityRequirement.NONE);
        assertThat(result.sourcePlan()).isEqualTo(SourcePlan.off());
    }

    @Test
    void catalogAndCanonicalIdShouldPrecedeTheDomainGate() {
        var unresolved = router.route(RagQuery.of("琴叶榕怎么样？"));
        assertThat(unresolved.domain()).isEqualTo(QueryRouter.QueryDomain.UNKNOWN);

        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        when(repository.findPlantEntities()).thenReturn(List.of(
                new KnowledgeRepository.PlantEntityRow("30", "Ficus lyrata", "琴叶榕", List.of("琴叶树"))));
        QueryRouter catalogRouter = new QueryRouter(new PlantCatalogIndex(repository, new PlantAliasMatcher()));

        assertThat(catalogRouter.route(RagQuery.of("琴叶榕怎么样？")).domain())
                .isEqualTo(QueryRouter.QueryDomain.PLANT);
        assertThat(catalogRouter.route(RagQuery.of("琴叶树怎么样？")).domain())
                .isEqualTo(QueryRouter.QueryDomain.PLANT);
        RagQuery explicit = new RagQuery("怎么样？", null, null, "30", null, List.of(), Map.of());
        assertThat(router.route(explicit).domain()).isEqualTo(QueryRouter.QueryDomain.PLANT);
    }
}
