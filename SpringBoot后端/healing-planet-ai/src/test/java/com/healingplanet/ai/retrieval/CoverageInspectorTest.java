package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.config.RagRuntimeConfig;
import com.healingplanet.ai.domain.KnowledgeDocument;
import com.healingplanet.ai.domain.KnowledgeSource;
import com.healingplanet.ai.domain.QueryIntent;
import com.healingplanet.ai.domain.RagQuery;
import com.healingplanet.ai.query.QueryAnalysis;
import com.healingplanet.ai.query.RetrievalConstraints;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CoverageInspectorTest {

    @Test
    void missingRequiredCommunityExpandsOnlyCommunityBudget() {
        RagProperties properties = new RagProperties();
        properties.getAdaptiveRecall().setMaxDenseTopK(60);
        properties.getAdaptiveRecall().setMaxSparseTopK(60);
        properties.getAdaptiveRecall().setMinUniqueLogicalCandidates(1);
        RagRuntimeConfig config = RagRuntimeConfig.from(properties);
        SourcePlan sourcePlan = new SourcePlan(SourcePlan.SourceRequirement.ALLOWED,
                SourcePlan.SourceRequirement.REQUIRED, SourcePlan.SourceRequirement.FORBIDDEN);
        RetrievalQueryGroup group = new RetrievalQueryGroup("Q1", "绿萝浇水经验", GroupRole.PRIMARY,
                Set.of("WATERING"), Set.of(), SourceScope.from(sourcePlan), true);
        RetrievalRequest request = new RetrievalRequest(RagQuery.of("绿萝浇水经验"),
                new QueryAnalysis(QueryIntent.GENERAL_CARE, Set.of(), Set.of("WATERING"), false, 0.9),
                RetrievalConstraints.defaults(), new RetrievalPlan(sourcePlan, true, true, false, Set.of(),
                Set.of("WATERING"), "绿萝浇水经验", List.of(group)), null, "绿萝浇水经验");

        RecallCoverage coverage = new CoverageInspector().inspect(request, List.of(candidate("guide", "Q1")), config);
        RecallBudget initial = new AdaptiveRecallPolicy().initial(config);
        RecallBudget expanded = new AdaptiveRecallPolicy().next(request, coverage, initial, config);

        assertThat(coverage.missingRequiredSources()).containsExactly(KnowledgeSource.COMMUNITY);
        assertThat(coverage.sufficient()).isFalse();
        assertThat(expanded.plantDenseTopK()).isEqualTo(30);
        assertThat(expanded.plantSparseTopK()).isEqualTo(30);
        assertThat(expanded.communityDenseTopK()).isEqualTo(60);
        assertThat(expanded.communitySparseTopK()).isEqualTo(60);
    }

    @Test
    void groupLocalMissingCommunityDoesNotExpandAlreadyCoveredPlantSource() {
        RagProperties properties = new RagProperties();
        properties.getAdaptiveRecall().setMaxDenseTopK(60);
        properties.getAdaptiveRecall().setMaxSparseTopK(60);
        properties.getAdaptiveRecall().setMinUniqueLogicalCandidates(1);
        RagRuntimeConfig config = RagRuntimeConfig.from(properties);
        SourcePlan sourcePlan = new SourcePlan(SourcePlan.SourceRequirement.REQUIRED,
                SourcePlan.SourceRequirement.REQUIRED, SourcePlan.SourceRequirement.FORBIDDEN);
        RetrievalQueryGroup first = new RetrievalQueryGroup("Q1", "绿萝养护", GroupRole.ENTITY_FOCUS,
                Set.of(), Set.of(), SourceScope.from(sourcePlan), true);
        RetrievalQueryGroup second = new RetrievalQueryGroup("Q2", "虎尾兰养护", GroupRole.ENTITY_FOCUS,
                Set.of(), Set.of(), SourceScope.from(sourcePlan), true);
        RetrievalRequest request = new RetrievalRequest(RagQuery.of("绿萝和虎尾兰的官方与社区养护"),
                new QueryAnalysis(QueryIntent.GENERAL_CARE, Set.of(), Set.of(), false, 0.9),
                RetrievalConstraints.defaults(), new RetrievalPlan(sourcePlan, true, true, false, Set.of(), Set.of(),
                "绿萝和虎尾兰的官方与社区养护", List.of(first, second)), null, "绿萝和虎尾兰的官方与社区养护");

        RecallCoverage coverage = new CoverageInspector().inspect(request, List.of(
                candidate("guide-q1", "Q1"), communityCandidate("post-q1", "Q1"), candidate("guide-q2", "Q2")), config);
        RecallBudget expanded = new AdaptiveRecallPolicy().next(request, coverage,
                new AdaptiveRecallPolicy().initial(config), config);

        assertThat(coverage.missingRequiredSources()).isEmpty();
        assertThat(coverage.groups().get("Q2").missingSources()).containsExactly(KnowledgeSource.COMMUNITY);
        assertThat(expanded.plantDenseTopK()).isEqualTo(30);
        assertThat(expanded.plantSparseTopK()).isEqualTo(30);
        assertThat(expanded.communityDenseTopK()).isEqualTo(60);
        assertThat(expanded.communitySparseTopK()).isEqualTo(60);
    }

    @Test
    void splitEntityTopicCoverageAcrossGroupsDoesNotSatisfyCoverage() {
        RagProperties properties = new RagProperties();
        properties.getAdaptiveRecall().setMinUniqueLogicalCandidates(2);
        RagRuntimeConfig config = RagRuntimeConfig.from(properties);
        SourcePlan sourcePlan = new SourcePlan(SourcePlan.SourceRequirement.REQUIRED,
                SourcePlan.SourceRequirement.FORBIDDEN, SourcePlan.SourceRequirement.FORBIDDEN);
        RetrievalQueryGroup left = new RetrievalQueryGroup("Q1", "绿萝浇水湿度", GroupRole.ENTITY_FOCUS,
                Set.of("WATERING", "HUMIDITY"), Set.of("1"), SourceScope.from(sourcePlan), true);
        RetrievalQueryGroup right = new RetrievalQueryGroup("Q2", "虎尾兰浇水湿度", GroupRole.ENTITY_FOCUS,
                Set.of("WATERING", "HUMIDITY"), Set.of("2"), SourceScope.from(sourcePlan), true);
        RetrievalRequest request = new RetrievalRequest(RagQuery.of("对比绿萝和虎尾兰的浇水湿度"),
                new QueryAnalysis(QueryIntent.GENERAL_CARE, Set.of(), Set.of("WATERING", "HUMIDITY"), false, 0.9),
                RetrievalConstraints.defaults(), new RetrievalPlan(sourcePlan, true, false, false, Set.of(),
                Set.of("WATERING", "HUMIDITY"), "对比绿萝和虎尾兰的浇水湿度", List.of(left, right)), null,
                "对比绿萝和虎尾兰的浇水湿度");

        RecallCoverage coverage = new CoverageInspector().inspect(request,
                List.of(candidate("water", "Q1", "1", "WATERING"), candidate("humidity", "Q2", "2", "HUMIDITY")), config);

        assertThat(coverage.coveredRequiredQueryGroups()).isEmpty();
        assertThat(coverage.coveredEntities()).containsExactlyInAnyOrder("1", "2");
        assertThat(coverage.coveredTopics()).containsExactlyInAnyOrder("WATERING", "HUMIDITY");
        assertThat(coverage.groups().get("Q1").missingTopics()).containsExactly("HUMIDITY");
        assertThat(coverage.groups().get("Q2").missingTopics()).containsExactly("WATERING");
        assertThat(coverage.missingRequiredQueryGroups()).containsExactlyInAnyOrder("Q1", "Q2");
        assertThat(coverage.sufficient()).isFalse();
    }

    @Test
    void allRequiredFacetsMustBeCoveredWithinEachGroup() {
        RagProperties properties = new RagProperties();
        properties.getAdaptiveRecall().setMinUniqueLogicalCandidates(2);
        RagRuntimeConfig config = RagRuntimeConfig.from(properties);
        SourcePlan sourcePlan = new SourcePlan(SourcePlan.SourceRequirement.REQUIRED,
                SourcePlan.SourceRequirement.FORBIDDEN, SourcePlan.SourceRequirement.FORBIDDEN);
        RetrievalQueryGroup left = new RetrievalQueryGroup("Q1", "绿萝浇水湿度", GroupRole.ENTITY_FOCUS,
                Set.of("WATERING", "HUMIDITY"), Set.of("1"), SourceScope.from(sourcePlan), true);
        RetrievalQueryGroup right = new RetrievalQueryGroup("Q2", "虎尾兰浇水湿度", GroupRole.ENTITY_FOCUS,
                Set.of("WATERING", "HUMIDITY"), Set.of("2"), SourceScope.from(sourcePlan), true);
        RetrievalRequest request = new RetrievalRequest(RagQuery.of("对比绿萝和虎尾兰的浇水湿度"),
                new QueryAnalysis(QueryIntent.GENERAL_CARE, Set.of(), Set.of("WATERING", "HUMIDITY"), false, 0.9),
                RetrievalConstraints.defaults(), new RetrievalPlan(sourcePlan, true, false, false, Set.of(),
                Set.of("WATERING", "HUMIDITY"), "对比绿萝和虎尾兰的浇水湿度", List.of(left, right)), null,
                "对比绿萝和虎尾兰的浇水湿度");

        RecallCoverage coverage = new CoverageInspector().inspect(request, List.of(
                candidate("water-left", "Q1", "1", "WATERING"),
                candidate("humidity-left", "Q1", "1", "HUMIDITY"),
                candidate("water-right", "Q2", "2", "WATERING"),
                candidate("humidity-right", "Q2", "2", "HUMIDITY")), config);

        assertThat(coverage.coveredRequiredQueryGroups()).containsExactlyInAnyOrder("Q1", "Q2");
        assertThat(coverage.groups().get("Q1").sufficient()).isTrue();
        assertThat(coverage.groups().get("Q2").sufficient()).isTrue();
        assertThat(coverage.sufficient()).isTrue();
    }

    @Test
    void oneCommunityPostCannotCoverEveryEntityFocusedGroupWithoutResolvedAffinity() {
        RagProperties properties = new RagProperties();
        properties.getAdaptiveRecall().setMinUniqueLogicalCandidates(1);
        RagRuntimeConfig config = RagRuntimeConfig.from(properties);
        SourcePlan sourcePlan = new SourcePlan(SourcePlan.SourceRequirement.FORBIDDEN,
                SourcePlan.SourceRequirement.REQUIRED, SourcePlan.SourceRequirement.FORBIDDEN);
        RetrievalQueryGroup left = new RetrievalQueryGroup("Q1", "对比绿萝和虎尾兰", GroupRole.ENTITY_FOCUS,
                Set.of(), Set.of("1"), SourceScope.from(sourcePlan), true);
        RetrievalQueryGroup right = new RetrievalQueryGroup("Q2", "对比绿萝和虎尾兰", GroupRole.ENTITY_FOCUS,
                Set.of(), Set.of("2"), SourceScope.from(sourcePlan), true);
        RetrievalRequest request = new RetrievalRequest(RagQuery.of("对比绿萝和虎尾兰，社区分别怎么养？"),
                new QueryAnalysis(QueryIntent.COMMUNITY_SEARCH, Set.of(), Set.of(), false, 0.9),
                RetrievalConstraints.defaults(), new RetrievalPlan(sourcePlan, false, true, false, Set.of(),
                Set.of(), "对比绿萝和虎尾兰，社区分别怎么养？", List.of(left, right)), null,
                "对比绿萝和虎尾兰，社区分别怎么养？");
        KnowledgeDocument document = new KnowledgeDocument("post", KnowledgeSource.COMMUNITY, "post", "绿萝经验",
                "绿萝经验", "1", "绿萝", "COMMUNITY_EXPERIENCE", List.of(), 0.5, false,
                0, 0, 0, 0, Instant.EPOCH, Map.of("resolvedPlantIds", "1"));
        LogicalEvidenceCandidate candidate = new LogicalEvidenceCandidate("COMMUNITY:post", document,
                List.of(RetrievalFragmentHit.dense(document, 1, 0.9)), 1, null, 0.9, null, 0.1,
                Set.of("Q1", "Q2"));

        RecallCoverage coverage = new CoverageInspector().inspect(request, List.of(candidate), config);

        assertThat(coverage.groups().get("Q1").coveredSources()).containsExactly(KnowledgeSource.COMMUNITY);
        assertThat(coverage.groups().get("Q2").missingSources()).containsExactly(KnowledgeSource.COMMUNITY);
        assertThat(coverage.missingRequiredQueryGroups()).containsExactly("Q2");
        assertThat(coverage.sufficient()).isFalse();
    }

    private LogicalEvidenceCandidate candidate(String id, String groupId) {
        return candidate(id, groupId, "1", "WATERING");
    }

    private LogicalEvidenceCandidate candidate(String id, String groupId, String plantId, String knowledgeType) {
        KnowledgeDocument document = new KnowledgeDocument(id, KnowledgeSource.PLANT, plantId, id, id, plantId,
                "植物", knowledgeType, List.of(), 1, false, 0, 0, 0, 0, Instant.EPOCH, Map.of());
        return new LogicalEvidenceCandidate("PLANT:" + id, document,
                List.of(RetrievalFragmentHit.dense(document, 1, 1)), 1, null, 1d, null, 0.1, Set.of(groupId));
    }

    private LogicalEvidenceCandidate communityCandidate(String id, String groupId) {
        KnowledgeDocument document = new KnowledgeDocument(id, KnowledgeSource.COMMUNITY, id, id, id, "",
                "社区", "COMMUNITY_EXPERIENCE", List.of(), 0.5, false, 0, 0, 0, 0, Instant.EPOCH, Map.of());
        return new LogicalEvidenceCandidate("COMMUNITY:" + id, document,
                List.of(RetrievalFragmentHit.dense(document, 1, 1)), 1, null, 1d, null, 0.1, Set.of(groupId));
    }
}
