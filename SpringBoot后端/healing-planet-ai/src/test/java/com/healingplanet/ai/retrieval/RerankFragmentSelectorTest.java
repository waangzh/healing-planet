package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.KnowledgeDocument;
import com.healingplanet.ai.domain.KnowledgeSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RerankFragmentSelectorTest {

    private final RerankFragmentSelector selector = new RerankFragmentSelector();

    @Test
    void shouldKeepOnlyBestRetrievalFragmentsForEachLogicalEvidence() {
        LogicalEvidenceCandidate longPost = candidate("post", List.of(
                fragment("A", RetrievalPath.DENSE, 2),
                fragment("B", RetrievalPath.SPARSE, 3),
                fragment("C", RetrievalPath.DENSE, 27),
                fragment("D", RetrievalPath.SPARSE, 30)));

        List<RetrievalFragmentHit> result = selector.select(List.of(longPost), 20, 2, 40, 60);

        assertThat(result).extracting(RetrievalFragmentHit::fragmentId).containsExactly("A", "B");
    }

    @Test
    void shouldReserveFirstFragmentForEachLogicalEvidenceBeforeSupplementalFragments() {
        LogicalEvidenceCandidate first = candidate("first", List.of(
                fragment("first-main", RetrievalPath.DENSE, 1), fragment("first-extra", RetrievalPath.DENSE, 2)));
        LogicalEvidenceCandidate second = candidate("second", List.of(
                fragment("second-main", RetrievalPath.DENSE, 3), fragment("second-extra", RetrievalPath.DENSE, 4)));

        List<RetrievalFragmentHit> result = selector.select(List.of(first, second), 20, 2, 3, 60);

        assertThat(result).extracting(RetrievalFragmentHit::fragmentId)
                .containsExactly("first-main", "second-main", "first-extra");
    }

    private LogicalEvidenceCandidate candidate(String id, List<RetrievalFragmentHit> fragments) {
        return new LogicalEvidenceCandidate(id, fragments.get(0).document(), fragments, 1, null, 0.9, null,
                0.1d, java.util.Set.of());
    }

    private RetrievalFragmentHit fragment(String id, RetrievalPath path, int rank) {
        KnowledgeDocument document = new KnowledgeDocument(id, KnowledgeSource.COMMUNITY, "post", "帖子", id,
                "", "绿萝", "COMMUNITY_EXPERIENCE", List.of(), 0.5, false,
                0, 0, 0, 0, Instant.EPOCH, Map.of("fragmentId", id, "logicalEvidenceId", "post"));
        return new RetrievalFragmentHit(id, "post", document, path, rank, 1d / rank);
    }
}
