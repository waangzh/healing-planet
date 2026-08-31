package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.EvidenceFragment;
import com.healingplanet.ai.domain.KnowledgeDocument;

import java.util.Objects;

record RetrievalFragmentHit(
        String fragmentId,
        String logicalEvidenceId,
        KnowledgeDocument document,
        RetrievalPath path,
        Integer rank,
        Double score
) {
    RetrievalFragmentHit {
        fragmentId = requireText(fragmentId, "fragmentId");
        logicalEvidenceId = requireText(logicalEvidenceId, "logicalEvidenceId");
        document = Objects.requireNonNull(document, "document 不能为空");
        path = Objects.requireNonNull(path, "path 不能为空");
        if (rank == null || rank < 1) {
            throw new IllegalArgumentException("rank 必须大于 0");
        }
    }

    static RetrievalFragmentHit dense(KnowledgeDocument document, int rank, double score) {
        return from(document, RetrievalPath.DENSE, rank, score);
    }

    static RetrievalFragmentHit sparse(KnowledgeDocument document, int rank, double score) {
        return from(document, RetrievalPath.SPARSE, rank, score);
    }

    private static RetrievalFragmentHit from(KnowledgeDocument document, RetrievalPath path, int rank, double score) {
        EvidenceFragment fragment = EvidenceFragment.from(document);
        return new RetrievalFragmentHit(fragment.id(), fragment.logicalEvidence().id(), document, path, rank, score);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return value;
    }
}
