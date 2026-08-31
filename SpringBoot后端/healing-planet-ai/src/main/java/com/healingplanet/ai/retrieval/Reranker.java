package com.healingplanet.ai.retrieval;

import java.util.List;
import java.util.Map;

public interface Reranker {
    Map<String, Double> rerank(String query, List<LogicalEvidenceCandidate> candidates);
}
