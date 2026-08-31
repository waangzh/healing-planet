package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.KnowledgeDocument;
import com.healingplanet.ai.domain.KnowledgeSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LogicalEvidenceCandidateMergerTest {

    @Test
    void sameLogicalEvidenceAcrossGroupsKeepsBestFusionInsteadOfAddingScores() {
        KnowledgeDocument first = document("fragment-1");
        KnowledgeDocument second = document("fragment-2");
        LogicalEvidenceCandidate high = candidate(first, 0.03);
        LogicalEvidenceCandidate low = candidate(second, 0.02);

        List<LogicalEvidenceCandidate> merged = new LogicalEvidenceCandidateMerger().merge(List.of(
                new LogicalEvidenceCandidateMerger.GroupCandidate("Q1", high),
                new LogicalEvidenceCandidateMerger.GroupCandidate("Q2", low)));

        assertThat(merged).singleElement().satisfies(candidate -> {
            assertThat(candidate.fusionScore()).isEqualTo(0.03);
            assertThat(candidate.representative().id()).isEqualTo("fragment-1");
            assertThat(candidate.matchedQueryGroupIds()).containsExactlyInAnyOrder("Q1", "Q2");
            assertThat(candidate.fragments()).hasSize(2);
        });
    }

    private LogicalEvidenceCandidate candidate(KnowledgeDocument document, double fusion) {
        return new LogicalEvidenceCandidate("PLANT:1:WATERING", document,
                List.of(RetrievalFragmentHit.dense(document, 1, 0.9)), 1, null, 0.9, null, fusion);
    }

    private KnowledgeDocument document(String id) {
        return new KnowledgeDocument(id, KnowledgeSource.PLANT, "1", id, id, "1", "绿萝", "WATERING",
                List.of(), 1, false, 0, 0, 0, 0, Instant.EPOCH, Map.of());
    }
}
