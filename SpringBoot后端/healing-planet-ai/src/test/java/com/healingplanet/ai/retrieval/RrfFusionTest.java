package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.KnowledgeDocument;
import com.healingplanet.ai.domain.KnowledgeSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

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

        assertThat(result).extracting(candidate -> candidate.document().id())
                .containsExactly("shared", "dense", "sparse");
        assertThat(result.get(0).denseRank()).isEqualTo(2);
        assertThat(result.get(0).sparseRank()).isEqualTo(1);
    }

    private KnowledgeDocument document(String id) {
        return new KnowledgeDocument(id, KnowledgeSource.PLANT, id, id, id, id, "绿萝", "CARE",
                List.of(), 1, false, 0, 0, 0, 0, Instant.EPOCH);
    }
}
