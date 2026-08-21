package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.Evidence;
import com.healingplanet.ai.domain.EvidenceType;
import com.healingplanet.ai.domain.QueryIntent;
import com.healingplanet.ai.domain.RagQuery;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceSelectorTest {

    private final EvidenceSelector selector = new EvidenceSelector();

    @Test
    void shouldKeepBestEvidenceForEachExplicitKnowledgeType() {
        RagQuery query = new RagQuery("绿萝的光照和湿度要求", null, null, null, null, List.of(),
                Map.of("includeCommunity", false, "requiredKnowledgeTypes", List.of("LIGHT", "HUMIDITY")));

        EvidenceSelector.Selection result = selector.select(request(query,
                SourcePlan.SourceRequirement.REQUIRED, SourcePlan.SourceRequirement.OFF, Set.of("LIGHT", "HUMIDITY")), List.of(
                guide("light-best", "plant-1", "LIGHT", 0.95),
                guide("light-duplicate", "plant-1", "LIGHT", 0.90),
                guide("humidity-best", "plant-1", "HUMIDITY", 0.85)), 6, List.of("plant-1"));

        assertThat(result.evidence()).extracting(Evidence::id)
                .containsExactly("light-best", "humidity-best");
        assertThat(result.reasons()).containsEntry("light-best", "SOURCE_RETENTION")
                .containsEntry("humidity-best", "TOPIC_COVERAGE");
    }

    @Test
    void shouldCoverBroadCareTopicsWhileDeduplicatingLogicalTopicGroups() {
        RagQuery query = new RagQuery("绿萝怎么养？", null, null, null, QueryIntent.GENERAL_CARE, List.of(),
                Map.of("includeCommunity", false));

        EvidenceSelector.Selection result = selector.select(request(query,
                SourcePlan.SourceRequirement.REQUIRED, SourcePlan.SourceRequirement.OFF, Set.of()), List.of(
                guide("general-1", "plant-1", "GENERAL_CARE", 0.99),
                guide("general-2", "plant-1", "GENERAL_CARE", 0.98),
                guide("light", "plant-1", "LIGHT", 0.80),
                guide("watering", "plant-1", "WATERING", 0.79)), 6, List.of("plant-1"));

        assertThat(result.evidence()).extracting(Evidence::id)
                .containsExactly("general-1", "light", "watering");
        assertThat(result.reasons()).containsEntry("general-1", "SOURCE_RETENTION")
                .containsEntry("light", "BROAD_CARE_COVERAGE");
    }

    @Test
    void shouldReserveFormalKnowledgeAndTwoDistinctCommunityPostsForMixedQuery() {
        RagQuery query = new RagQuery("绿萝浇水的官方建议与社区经验", null, null, null, null, List.of(),
                Map.of("includePlantKnowledge", true, "includeCommunity", true,
                        "requiredKnowledgeType", "WATERING"));

        EvidenceSelector.Selection result = selector.select(request(query,
                SourcePlan.SourceRequirement.REQUIRED, SourcePlan.SourceRequirement.REQUIRED, Set.of("WATERING")), List.of(
                guide("guide", "plant-1", "WATERING", 0.99),
                community("post-a-1", "post-a", 0.98),
                community("post-a-2", "post-a", 0.97),
                community("post-b", "post-b", 0.96),
                community("post-c", "post-c", 0.95)), 6, List.of("plant-1"));

        assertThat(result.evidence()).extracting(Evidence::id)
                .containsExactly("guide", "post-a-1", "post-b");
        assertThat(result.reasons()).containsEntry("guide", "SOURCE_RETENTION")
                .containsEntry("post-a-1", "SOURCE_RETENTION")
                .containsEntry("post-b", "SOURCE_RETENTION");
    }

    @Test
    void shouldReserveTwoCommunitySourcesBeforeHigherRankedPlantTopicsFillCapacity() {
        RagQuery query = new RagQuery("绿萝怎么养？大家有什么经验？", null, null, null,
                QueryIntent.GENERAL_CARE, List.of(),
                Map.of("includePlantKnowledge", true, "includeCommunity", true));

        EvidenceSelector.Selection result = selector.select(request(query,
                SourcePlan.SourceRequirement.REQUIRED, SourcePlan.SourceRequirement.REQUIRED, Set.of()), List.of(
                guide("general", "plant-1", "GENERAL_CARE", 0.99),
                guide("temperature", "plant-1", "TEMPERATURE", 0.98),
                guide("light", "plant-1", "LIGHT", 0.97),
                guide("watering", "plant-1", "WATERING", 0.96),
                community("post-a", "post-a", 0.80),
                community("post-b", "post-b", 0.79)), 4, List.of("plant-1"));

        assertThat(result.evidence()).extracting(Evidence::id)
                .containsExactly("general", "temperature", "post-a", "post-b");
        assertThat(result.evidence()).filteredOn(evidence -> evidence.type() == EvidenceType.COMMUNITY_POST)
                .extracting(Evidence::sourceId).containsExactly("post-a", "post-b");
    }

    private Evidence guide(String id, String plantId, String knowledgeType, double score) {
        return evidence(id, EvidenceType.CARE_GUIDE, plantId, knowledgeType, score, plantId);
    }

    private Evidence community(String id, String sourceId, double score) {
        return evidence(id, EvidenceType.COMMUNITY_POST, sourceId, "COMMUNITY_EXPERIENCE", score, "");
    }

    private Evidence evidence(String id, EvidenceType type, String sourceId, String knowledgeType,
                              double score, String canonicalPlantId) {
        return new Evidence(id, type, sourceId, type.name(), id, id, score, score, 1d, score,
                Map.of("knowledgeType", knowledgeType, "canonicalPlantId", canonicalPlantId), null);
    }

    private RetrievalRequest request(RagQuery query, SourcePlan.SourceRequirement knowledge,
                                     SourcePlan.SourceRequirement community, Set<String> types) {
        SourcePlan sourcePlan = new SourcePlan(knowledge, community, SourcePlan.SourceRequirement.OFF);
        QueryRouter.RoutingDecision routing = new QueryRouter.RoutingDecision(sourcePlan,
                QueryIntent.GENERAL_CARE, QueryRouter.StateEvidenceNeed.NONE);
        return new RetrievalRequest(query, routing, sourcePlan, query.query(), types);
    }
}
