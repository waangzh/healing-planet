package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.RagQuery;
import com.healingplanet.ai.query.ExplicitConstraintParser;
import com.healingplanet.ai.query.QueryAnalyzer;
import com.healingplanet.ai.query.StateNeed;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Regression tests for evidence-first query understanding and planning. */
class QueryAnalysisTest {
    private final QueryAnalyzer analyzer = new QueryAnalyzer();
    private final ExplicitConstraintParser constraints = new ExplicitConstraintParser();
    private final RetrievalPlanner planner = new RetrievalPlanner();

    @Test
    void naturalLanguagePlantStateVariantRetainsRecoverableStateNeeds() {
        var analysis = analyzer.analyze(RagQuery.of("它是不是有点渴了？"));

        assertThat(analysis.stateNeeds()).contains(StateNeed.CURRENT, StateNeed.DECISION_SUPPORT);
        assertThat(analysis.plantDomainConfidence()).isGreaterThan(0d);
        assertThat(plan("它是不是有点渴了？").searchKnowledge()).isTrue();
        assertThat(plan("它是不是有点渴了？").searchCommunity()).isTrue();
    }

    @Test
    void returnFromTripQuestionKeepsStateRetrievalRatherThanDomainGate() {
        var plan = plan("我出差一周回来，它是不是缺水了？");

        assertThat(plan.searchState()).isTrue();
        assertThat(plan.stateNeeds()).contains(StateNeed.CURRENT, StateNeed.DECISION_SUPPORT);
        assertThat(plan.sourcePlan().knowledge()).isEqualTo(SourcePlan.SourceRequirement.ALLOWED);
    }

    @Test
    void compoundStateQuestionPreservesEveryIndependentNeed() {
        var analysis = analyzer.analyze(RagQuery.of("绿萝现在湿度多少，过去一天变化怎么样，需要浇水吗？"));

        assertThat(analysis.stateNeeds()).containsExactlyInAnyOrder(
                StateNeed.CURRENT, StateNeed.HISTORY, StateNeed.DECISION_SUPPORT);
        assertThat(plan("绿萝现在湿度多少，过去一天变化怎么样，需要浇水吗？").searchState()).isTrue();
    }

    @Test
    void freshnessAndHistoryCanComposeWithCurrentState() {
        var analysis = analyzer.analyze(RagQuery.of("虎尾兰最近温度变化大不大，现在数据还靠谱吗？"));

        assertThat(analysis.stateNeeds()).containsExactlyInAnyOrder(
                StateNeed.CURRENT, StateNeed.HISTORY, StateNeed.FRESHNESS);
    }

    @Test
    void explicitMixedSourceRequestMakesBothSourcesRequired() {
        var plan = plan("官方怎么说，大家平时又怎么做？");

        assertThat(plan.searchKnowledge()).isTrue();
        assertThat(plan.searchCommunity()).isTrue();
        assertThat(plan.sourcePlan().knowledge()).isEqualTo(SourcePlan.SourceRequirement.REQUIRED);
        assertThat(plan.sourcePlan().community()).isEqualTo(SourcePlan.SourceRequirement.REQUIRED);
    }

    @Test
    void communityOnlyAndFormalOnlyAreHardConstraints() {
        var communityOnly = plan("只看社区经验。");
        var formalOnly = plan("不要引用帖子，只按正式指南回答。");

        assertThat(communityOnly.searchKnowledge()).isFalse();
        assertThat(communityOnly.searchCommunity()).isTrue();
        assertThat(communityOnly.sourcePlan().knowledge()).isEqualTo(SourcePlan.SourceRequirement.FORBIDDEN);
        assertThat(formalOnly.searchKnowledge()).isTrue();
        assertThat(formalOnly.searchCommunity()).isFalse();
        assertThat(formalOnly.sourcePlan().community()).isEqualTo(SourcePlan.SourceRequirement.FORBIDDEN);
    }

    @Test
    void plantScienceQuestionIsNotRejectedBeforeRetrieval() {
        var plan = plan("植物为什么会进行光合作用？");

        assertThat(plan.searchKnowledge()).isTrue();
        assertThat(plan.searchCommunity()).isTrue();
    }

    @Test
    void outOfScopeHintNeverTurnsIntoSourceForbiddance() {
        var plan = plan("量子纠缠是什么？");

        assertThat(analyzer.analyze(RagQuery.of("量子纠缠是什么？")).plantDomainConfidence()).isLessThan(0.3d);
        assertThat(plan.searchKnowledge()).isTrue();
        assertThat(plan.searchCommunity()).isTrue();
    }

    @Test
    void topicHintsAreMultiValueSoftSignals() {
        var analysis = analyzer.analyze(RagQuery.of("房间空气干燥而且阳光直射"));

        assertThat(analysis.topicHints()).containsExactlyInAnyOrder("LIGHT", "HUMIDITY");
        assertThat(plan("房间空气干燥而且阳光直射").topicHints()).isEqualTo(analysis.topicHints());
    }

    private RetrievalPlan plan(String text) {
        RagQuery query = RagQuery.of(text);
        var analysis = analyzer.analyze(query);
        return planner.plan(query, analysis, constraints.parse(query), null);
    }
}
