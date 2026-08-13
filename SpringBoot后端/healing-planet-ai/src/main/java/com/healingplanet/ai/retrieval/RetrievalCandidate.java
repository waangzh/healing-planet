package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.KnowledgeDocument;

record RetrievalCandidate(
        KnowledgeDocument document,
        Double denseScore,
        Double sparseScore,
        int denseRank,
        int sparseRank,
        double fusionScore
) {
}
