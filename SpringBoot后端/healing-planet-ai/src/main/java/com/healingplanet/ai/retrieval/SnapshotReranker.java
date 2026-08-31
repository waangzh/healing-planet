package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.config.RagRuntimeSnapshot;

import java.util.List;
import java.util.Map;

interface SnapshotReranker extends Reranker {
    Map<String, Double> rerank(String query, List<LogicalEvidenceCandidate> candidates,
                                RagRuntimeSnapshot runtimeSnapshot);
}
