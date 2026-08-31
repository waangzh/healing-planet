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
    void candidateFromEachRequiredEntityAndTopicSatisfiesCoverage() {
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

        assertThat(coverage.coveredRequiredQueryGroups()).containsExactlyInAnyOrder("Q1", "Q2");
        assertThat(coverage.coveredEntities()).containsExactlyInAnyOrder("1", "2");
        assertThat(coverage.coveredTopics()).containsExactlyInAnyOrder("WATERING", "HUMIDITY");
        assertThat(coverage.sufficient()).isTrue();
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
}
