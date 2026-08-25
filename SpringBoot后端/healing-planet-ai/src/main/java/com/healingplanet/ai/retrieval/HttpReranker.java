package com.healingplanet.ai.retrieval;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.config.RagRuntimeConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
class HttpReranker implements SnapshotReranker {

    private final RestClient client;
    private final RagProperties properties;

    HttpReranker(@Qualifier("rerankerRestClient") RestClient client, RagProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public Map<String, Double> rerank(String query, List<RetrievalCandidate> candidates) {
        return rerank(query, candidates, RagRuntimeConfig.from(properties));
    }

    @Override
    public Map<String, Double> rerank(String query, List<RetrievalCandidate> candidates, RagRuntimeConfig config) {
        if (!config.rerankerEnabled() || candidates.isEmpty()) return Map.of();
        int configuredTopK = properties.getReranker().getCandidateTopK();
        List<RetrievalCandidate> rerankCandidates = configuredTopK > 0
                ? candidates.stream().limit(configuredTopK).toList() : candidates;
        List<String> documents = rerankCandidates.stream()
                .map(candidate -> candidate.document().content()).toList();
        RerankResponse response = client.post().uri(properties.getReranker().getPath())
                .body(new RerankRequest(properties.getReranker().getModel(), query, documents))
                .retrieve().body(RerankResponse.class);
        if (response == null || response.results() == null) return Map.of();
        Map<String, Double> scores = new HashMap<>();
        response.results().forEach(result -> {
            if (result.index() >= 0 && result.index() < rerankCandidates.size()) {
                scores.put(rerankCandidates.get(result.index()).document().id(), result.score());
            }
        });
        return scores;
    }

    private record RerankRequest(String model, String query, List<String> documents) { }
    private record RerankResponse(List<RerankResult> results) { }
    private record RerankResult(int index, @JsonProperty("relevance_score") double score) { }
}
