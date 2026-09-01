package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.config.RagRuntimeSnapshot;

import java.util.List;
import java.util.Map;

/** Optional extension for rerankers that need query-group coverage to choose their finite admission set. */
interface RequestAwareSnapshotReranker extends SnapshotReranker {
    Map<String, Double> rerank(RetrievalRequest request, String query, List<LogicalEvidenceCandidate> candidates,
                                RagRuntimeSnapshot runtimeSnapshot);
}
