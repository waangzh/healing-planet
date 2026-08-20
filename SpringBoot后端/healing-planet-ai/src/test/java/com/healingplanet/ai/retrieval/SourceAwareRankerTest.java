package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.Evidence;
import com.healingplanet.ai.domain.KnowledgeDocument;
import com.healingplanet.ai.domain.KnowledgeSource;
import com.healingplanet.ai.domain.RagQuery;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SourceAwareRankerTest {

    private final SourceAwareRanker ranker = new SourceAwareRanker();

    @Test
    void shouldReturnAllCandidatesInRerankedOrderWithoutApplyingSelectionLimit() {
        List<RetrievalCandidate> candidates = List.of(
                candidate("strong"), candidate("medium"), candidate("tail"), candidate("last"));
        Map<String, Double> rerankScores = Map.of(
                "strong", 1.0,
                "medium", 0.98,
                "tail", 0.5,
                "last", 0.2);

        List<Evidence> result = ranker.rank(RagQuery.of("光照"), candidates, rerankScores);

        assertThat(result).extracting(Evidence::id).containsExactly("strong", "medium", "tail", "last");
    }

    @Test
    void shouldKeepBestAvailableEvidenceBelowOldMinimumScore() {
        List<Evidence> result = ranker.rank(RagQuery.of("光照"),
                List.of(candidate("weak")), Map.of("weak", 0.4));

        assertThat(result).extracting(Evidence::id).containsExactly("weak");
    }

    @Test
    void shouldApplyRelativeAdmissionWithinEachSourceForMixedQueries() {
        List<RetrievalCandidate> candidates = List.of(
                candidate("guide"), communityCandidate("community"));
        Map<String, Double> rerankScores = Map.of(
                "guide", 1.0,
                "community", 0.8);

        List<Evidence> result = ranker.rank(RagQuery.of("绿萝官方建议，社区经验怎么做？"),
                candidates, rerankScores);

        assertThat(result).extracting(Evidence::id).containsExactly("guide", "community");
    }

    @Test
    void shouldKeepCommunityTailCandidateForRecall() {
        List<RetrievalCandidate> candidates = List.of(
                candidate("guide"), communityCandidate("relevant"), communityCandidate("irrelevant"));
        Map<String, Double> rerankScores = Map.of(
                "guide", 1.0,
                "relevant", 0.8,
                "irrelevant", 0.4);

        List<Evidence> result = ranker.rank(RagQuery.of("绿萝官方建议，社区经验怎么做？"),
                candidates, rerankScores);

        assertThat(result).extracting(Evidence::id).containsExactly("guide", "relevant", "irrelevant");
    }

    @Test
    void disabledSourceAwareRankingShouldPreservePureRrfOrder() {
        var properties = new com.healingplanet.ai.config.RagProperties();
        properties.getSourceAwareRanking().setEnabled(false);
        SourceAwareRanker baselineRanker = new SourceAwareRanker(properties);
        RetrievalCandidate denseFavored = candidate("dense-favored", 0.99, 0.01);
        RetrievalCandidate rrfFavored = candidate("rrf-favored", 0.20, 0.03);

        List<Evidence> result = baselineRanker.rank(RagQuery.of("光照"),
                List.of(rrfFavored, denseFavored), Map.of());

        assertThat(result).extracting(Evidence::id).containsExactly("rrf-favored", "dense-favored");
    }

    private RetrievalCandidate candidate(String id) {
        KnowledgeDocument document = new KnowledgeDocument(id, KnowledgeSource.PLANT, "1", id, id,
                "1", "绿萝", "LIGHT", List.of("光照"), 1, false,
                0, 0, 0, 0, Instant.EPOCH, Map.of());
        return new RetrievalCandidate(document, null, null, 0, 0, 0);
    }

    private RetrievalCandidate candidate(String id, double denseScore, double fusionScore) {
        KnowledgeDocument document = new KnowledgeDocument(id, KnowledgeSource.PLANT, "1", id, id,
                "1", "绿萝", "LIGHT", List.of("光照"), 1, false,
                0, 0, 0, 0, Instant.EPOCH, Map.of());
        return new RetrievalCandidate(document, denseScore, null, 1, 0, fusionScore);
    }

    private RetrievalCandidate communityCandidate(String id) {
        KnowledgeDocument document = new KnowledgeDocument(id, KnowledgeSource.COMMUNITY, "community", id, id,
                "", "绿萝", "COMMUNITY_EXPERIENCE", List.of(), 0.5, false,
                0, 0, 0, 0, Instant.EPOCH, Map.of());
        return new RetrievalCandidate(document, null, null, 0, 0, 0);
    }
}
