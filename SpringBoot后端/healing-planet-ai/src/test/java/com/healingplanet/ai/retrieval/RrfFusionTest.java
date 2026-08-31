package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.KnowledgeDocument;
import com.healingplanet.ai.domain.KnowledgeSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RrfFusionTest {

    @Test
    void shouldPreferDocumentFoundByBothRetrievers() {
        KnowledgeDocument shared = document("shared");
        KnowledgeDocument denseOnly = document("dense");
        KnowledgeDocument sparseOnly = document("sparse");

        var result = RrfFusion.fuse(
                List.of(new RrfFusion.DenseHit(denseOnly, 0.9), new RrfFusion.DenseHit(shared, 0.8)),
                List.of(new SparseIndexService.SparseHit(shared, 4.0),
                        new SparseIndexService.SparseHit(sparseOnly, 3.0)));

        assertThat(result).extracting(candidate -> candidate.representative().id())
                .containsExactly("shared", "dense", "sparse");
        assertThat(result.get(0).denseRank()).isEqualTo(2);
        assertThat(result.get(0).sparseRank()).isEqualTo(1);
    }

    @Test
    void shouldFuseFragmentsAtLogicalEvidenceLevelInsteadOfRewardingRepeatedChunks() {
        KnowledgeDocument firstFragment = fragment("plant-care", "fragment-1");
        KnowledgeDocument secondFragment = fragment("plant-care", "fragment-2");
        KnowledgeDocument corroboratedEvidence = fragment("watering-guide", "fragment-3");

        var result = RrfFusion.fuse(
                List.of(new RrfFusion.DenseHit(firstFragment, 0.95),
                        new RrfFusion.DenseHit(secondFragment, 0.90),
                        new RrfFusion.DenseHit(corroboratedEvidence, 0.80)),
                List.of(new SparseIndexService.SparseHit(corroboratedEvidence, 5.0)));

        assertThat(result).extracting(LogicalEvidenceCandidate::logicalEvidenceId)
                .containsExactly("watering-guide", "plant-care");
        assertThat(result.get(1).denseRank()).isEqualTo(1);
        assertThat(result.get(1).fusionScore()).isEqualTo(1d / 61d);
        assertThat(result.get(1).evidenceMetadata().get("matchedFragmentIds"))
                .isEqualTo("fragment-1,fragment-2");
    }

    @Test
    void shouldUseHighestRerankedFragmentAsLogicalEvidenceRepresentative() {
        KnowledgeDocument firstFragment = fragment("post-1", "fragment-1");
        KnowledgeDocument secondFragment = fragment("post-1", "fragment-2");

        LogicalEvidenceCandidate candidate = RrfFusion.fuse(
                List.of(new RrfFusion.DenseHit(firstFragment, 0.95), new RrfFusion.DenseHit(secondFragment, 0.90)),
                List.of()).get(0);

        LogicalEvidenceCandidate reranked = candidate.withRerankedRepresentative(Map.of("fragment-2", 0.99));

        assertThat(reranked.representative().id()).isEqualTo("fragment-2");
    }

    private KnowledgeDocument document(String id) {
        return new KnowledgeDocument(id, KnowledgeSource.PLANT, id, id, id, id, "绿萝", "CARE",
                List.of(), 1, false, 0, 0, 0, 0, Instant.EPOCH, Map.of());
    }

    private KnowledgeDocument fragment(String logicalEvidenceId, String fragmentId) {
        return new KnowledgeDocument(fragmentId, KnowledgeSource.PLANT, "plant-1", fragmentId, fragmentId,
                "plant-1", "绿萝", "CARE", List.of(), 1, false, 0, 0, 0, 0, Instant.EPOCH,
                Map.of("logicalEvidenceId", logicalEvidenceId, "fragmentId", fragmentId,
                        "fragmentRole", "CONTENT", "fragmentIndex", "0", "fragmentCount", "2"));
    }
}
