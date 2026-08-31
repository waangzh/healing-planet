package com.healingplanet.ai.retrieval;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.healingplanet.ai.config.RagRuntimeConfigProvider;
import com.healingplanet.ai.config.RagRuntimeSnapshot;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
class HttpReranker implements SnapshotReranker {

    private final RagRuntimeConfigProvider runtimeConfigProvider;

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
        int configuredTopK = client.candidateTopK();
        List<LogicalEvidenceCandidate> rerankCandidates = configuredTopK > 0
                ? candidates.stream().limit(configuredTopK).toList() : candidates;
        List<RetrievalFragmentHit> rerankFragments = uniqueFragments(rerankCandidates);
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

    private List<RetrievalFragmentHit> uniqueFragments(List<LogicalEvidenceCandidate> candidates) {
        Map<String, RetrievalFragmentHit> fragments = new LinkedHashMap<>();
        for (LogicalEvidenceCandidate candidate : candidates) {
            for (RetrievalFragmentHit fragment : candidate.fragments()) {
                fragments.putIfAbsent(fragment.fragmentId(), fragment);
            }
        }
        return List.copyOf(fragments.values());
    }

    private record RerankRequest(String model, String query, List<String> documents) { }
    private record RerankResponse(List<RerankResult> results) { }
    private record RerankResult(int index, @JsonProperty("relevance_score") double score) { }
}
