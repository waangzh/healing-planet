package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.Evidence;
import com.healingplanet.ai.domain.RagQuery;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PlantStateRetriever {
    private final PlantStateGateway client;
    private final PlantStateAnalyzer analyzer;
    private final RetrievalMetrics metrics;

    public PlantStateRetriever(PlantStateGateway client, PlantStateAnalyzer analyzer, RetrievalMetrics metrics) {
        this.client = client;
        this.analyzer = analyzer;
        this.metrics = metrics;
    }

    public List<Evidence> retrieve(RagQuery query) {
        if (query.plantInstanceId() == null || query.userId() == null) return List.of();
        return metrics.time("plant_state_query", "state",
                () -> client.get(query.plantInstanceId(), query.userId()))
                .map(analyzer::analyze).orElseGet(List::of);
    }
}
