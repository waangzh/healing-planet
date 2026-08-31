package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.KnowledgeSource;

/** Per-source recall limits, so an isolated source gap does not widen every search. */
record RecallBudget(int plantDenseTopK, int plantSparseTopK, int communityDenseTopK, int communitySparseTopK) {
    int denseTopK(KnowledgeSource source) {
        return source == KnowledgeSource.PLANT ? plantDenseTopK : communityDenseTopK;
    }

    int sparseTopK(KnowledgeSource source) {
        return source == KnowledgeSource.PLANT ? plantSparseTopK : communitySparseTopK;
    }
}
