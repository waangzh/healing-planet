package com.healingplanet.ai.retrieval;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.healingplanet.ai.config.RagRuntimeConfigProvider;
import com.healingplanet.ai.config.RagRuntimeSnapshot;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
class HttpReranker implements SnapshotReranker {

    private final RagRuntimeConfigProvider runtimeConfigProvider;
    private final RerankFragmentSelector fragmentSelector = new RerankFragmentSelector();

    HttpReranker(RagRuntimeConfigProvider runtimeConfigProvider) {
        this.runtimeConfigProvider = runtimeConfigProvider;
    }

    @Override
    public Map<String, Double> rerank(String query, List<LogicalEvidenceCandidate> candidates) {
        return rerank(query, candidates, runtimeConfigProvider.runtimeSnapshot());
    }

    @Override
    public Map<String, Double> rerank(String query, List<LogicalEvidenceCandidate> candidates,
                                       RagRuntimeSnapshot runtimeSnapshot) {
        var config = runtimeSnapshot.config();
        if (!config.rerankerEnabled() || candidates.isEmpty()) return Map.of();
        RagRuntimeSnapshot.RerankerRuntimeClient client = runtimeSnapshot.rerankerClient();
        if (client == null) return Map.of();
        List<RetrievalFragmentHit> rerankFragments = fragmentSelector.select(candidates, client.candidateTopK(),
                client.maxFragmentsPerLogicalEvidence(), client.maxFragmentsTotal(), config.rrfK());
        if (rerankFragments.isEmpty()) return Map.of();
        List<String> documents = rerankFragments.stream().map(fragment -> fragment.document().content()).toList();
        RerankResponse response = client.client().post().uri(client.path())
                .body(new RerankRequest(client.model(), query, documents))
                .retrieve().body(RerankResponse.class);
        if (response == null || response.results() == null) return Map.of();
        Map<String, Double> scores = new HashMap<>();
        response.results().forEach(result -> {
            if (result.index() >= 0 && result.index() < rerankFragments.size()) {
                scores.put(rerankFragments.get(result.index()).fragmentId(), result.score());
            }
        });
        return scores;
    }

    private record RerankRequest(String model, String query, List<String> documents) { }
    private record RerankResponse(List<RerankResult> results) { }
    private record RerankResult(int index, @JsonProperty("relevance_score") double score) { }
}
