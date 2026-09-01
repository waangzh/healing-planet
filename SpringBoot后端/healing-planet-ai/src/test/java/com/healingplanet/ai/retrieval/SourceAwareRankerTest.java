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
        List<LogicalEvidenceCandidate> candidates = List.of(
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
        List<LogicalEvidenceCandidate> candidates = List.of(
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
        List<LogicalEvidenceCandidate> candidates = List.of(
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
        LogicalEvidenceCandidate denseFavored = candidate("dense-favored", 0.99, 0.01);
        LogicalEvidenceCandidate rrfFavored = candidate("rrf-favored", 0.20, 0.03);

        List<Evidence> result = baselineRanker.rank(RagQuery.of("光照"),
                List.of(rrfFavored, denseFavored), Map.of());

        assertThat(result).extracting(Evidence::id).containsExactly("rrf-favored", "dense-favored");
    }

    @Test
    void shouldKeepRepresentativeAndBestSupplementalFragmentForPromptAssembly() {
        var properties = new com.healingplanet.ai.config.RagProperties();
        properties.getContextAssembly().setMaxFragmentsPerLogicalEvidence(2);
        SourceAwareRanker configuredRanker = new SourceAwareRanker(properties);
        KnowledgeDocument first = document("first");
        KnowledgeDocument best = document("best");
        KnowledgeDocument supplemental = document("supplemental");
        LogicalEvidenceCandidate candidate = new LogicalEvidenceCandidate("logical", first, List.of(
                RetrievalFragmentHit.dense(first, 1, 0.9), RetrievalFragmentHit.dense(best, 2, 0.8),
                RetrievalFragmentHit.dense(supplemental, 3, 0.7)), 1, null, 0.9, null, 0.1)
                .withRerankedRepresentative(Map.of("first", 0.4, "best", 0.9, "supplemental", 0.8));

        Evidence evidence = configuredRanker.rank(RagQuery.of("绿萝光照"), List.of(candidate),
                Map.of("first", 0.4, "best", 0.9, "supplemental", 0.8)).get(0);

        assertThat(evidence.id()).isEqualTo("best");
        List<?> contextFragments = (List<?>) evidence.metadata().get("contextFragments");
        assertThat(contextFragments).hasSize(2);
        assertThat(((Map<?, ?>) contextFragments.get(0)).get("content")).isEqualTo("best");
        assertThat(((Map<?, ?>) contextFragments.get(1)).get("content")).isEqualTo("supplemental");
    }

    private LogicalEvidenceCandidate candidate(String id) {
        KnowledgeDocument document = new KnowledgeDocument(id, KnowledgeSource.PLANT, "1", id, id,
                "1", "绿萝", "LIGHT", List.of("光照"), 1, false,
                0, 0, 0, 0, Instant.EPOCH, Map.of());
        return candidate(document, null, 0);
    }

    private LogicalEvidenceCandidate candidate(String id, double denseScore, double fusionScore) {
        KnowledgeDocument document = new KnowledgeDocument(id, KnowledgeSource.PLANT, "1", id, id,
                "1", "绿萝", "LIGHT", List.of("光照"), 1, false,
                0, 0, 0, 0, Instant.EPOCH, Map.of());
        return candidate(document, denseScore, fusionScore);
    }

    private LogicalEvidenceCandidate communityCandidate(String id) {
        KnowledgeDocument document = new KnowledgeDocument(id, KnowledgeSource.COMMUNITY, "community", id, id,
                "", "绿萝", "COMMUNITY_EXPERIENCE", List.of(), 0.5, false,
                0, 0, 0, 0, Instant.EPOCH, Map.of());
        return candidate(document, null, 0);
    }

    private LogicalEvidenceCandidate candidate(KnowledgeDocument document, Double denseScore, double fusionScore) {
        RetrievalFragmentHit fragment = RetrievalFragmentHit.dense(document, 1,
                denseScore == null ? 0d : denseScore);
        return new LogicalEvidenceCandidate(document.id(), document, List.of(fragment),
                denseScore == null ? null : 1, null, denseScore, null, fusionScore);
    }

    private KnowledgeDocument document(String id) {
        return new KnowledgeDocument(id, KnowledgeSource.PLANT, "1", id, id, "1", "绿萝", "LIGHT",
                List.of("光照"), 1, false, 0, 0, 0, 0, Instant.EPOCH,
                Map.of("fragmentId", id, "logicalEvidenceId", "logical"));
    }
}
