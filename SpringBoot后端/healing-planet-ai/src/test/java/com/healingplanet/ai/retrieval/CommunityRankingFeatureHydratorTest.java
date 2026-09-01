package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.KnowledgeDocument;
import com.healingplanet.ai.domain.KnowledgeSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommunityRankingFeatureHydratorTest {

    @Test
    void shouldReplaceOnlyCurrentCommunityRankingFeaturesAfterRecall() {
        CommunityRankingFeatureRepository repository = mock(CommunityRankingFeatureRepository.class);
        CommunityRankingFeatureHydrator hydrator = new CommunityRankingFeatureHydrator(repository);
        KnowledgeDocument document = new KnowledgeDocument("fragment", KnowledgeSource.COMMUNITY, "post-1",
                "绿萝经验", "绿萝浇水经验", "正文", "", "绿萝", "COMMUNITY_EXPERIENCE", List.of("绿萝"),
                0.5, false, 1, 2, 3, 4, Instant.parse("2026-01-01T00:00:00Z"),
                Map.of("resolvedPlantIds", "plant-1"));
        LogicalEvidenceCandidate candidate = new LogicalEvidenceCandidate("COMMUNITY:post-1", document,
                List.of(RetrievalFragmentHit.dense(document, 1, 0.9)), 1, null, 0.9, null, 0.1, Set.of("Q1"));
        when(repository.findByPostIds(Set.of("post-1"))).thenReturn(Map.of("post-1",
                new CommunityRankingFeatureRepository.CommunityRankingFeatures("post-1", 20, 10, 5, 200, true)));

        LogicalEvidenceCandidate hydrated = hydrator.hydrate(List.of(candidate)).get(0);

        assertThat(hydrated.representative()).extracting(KnowledgeDocument::likes, KnowledgeDocument::collects,
                KnowledgeDocument::comments, KnowledgeDocument::views, KnowledgeDocument::essence,
                KnowledgeDocument::trustScore).containsExactly(20, 10, 5, 200, true, 0.75d);
        assertThat(hydrated.representative().attributes()).containsEntry("resolvedPlantIds", "plant-1");
        assertThat(hydrated.fragments().get(0).document().views()).isEqualTo(200);
    }
}
