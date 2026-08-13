package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.Evidence;
import com.healingplanet.ai.domain.RagQuery;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Primary
@Service
public class StateAwareEvidenceRetriever implements EvidenceRetriever {
    private final QueryRouter router;
    private final HybridEvidenceRetriever knowledgeRetriever;
    private final PlantStateRetriever stateRetriever;

    public StateAwareEvidenceRetriever(QueryRouter router, HybridEvidenceRetriever knowledgeRetriever,
                                       PlantStateRetriever stateRetriever) {
        this.router = router;
        this.knowledgeRetriever = knowledgeRetriever;
        this.stateRetriever = stateRetriever;
    }

    @Override
    public List<Evidence> retrieve(RagQuery query) {
        QueryRouter.RoutingDecision route = router.route(query);
        List<Evidence> state = route.state() ? stateRetriever.retrieve(query) : List.of();
        RagQuery routed = routedQuery(query, route, state);
        List<Evidence> result = new ArrayList<>(state);
        if (route.knowledge() || route.community()) result.addAll(knowledgeRetriever.retrieve(routed));
        return List.copyOf(result);
    }

    private RagQuery routedQuery(RagQuery query, QueryRouter.RoutingDecision route, List<Evidence> state) {
        Map<String, Object> context = new HashMap<>(query.context());
        context.put("includePlantKnowledge", route.knowledge());
        context.put("includeCommunity", route.community());
        String plantName = state.stream().map(Evidence::metadata)
                .map(metadata -> metadata.get("plantName"))
                .filter(String.class::isInstance).map(String.class::cast)
                .filter(value -> !value.isBlank()).findFirst().orElse(null);
        String searchText = plantName == null || query.query().contains(plantName)
                ? query.query() : plantName + " " + query.query();
        return new RagQuery(searchText, query.userId(), query.plantInstanceId(), query.canonicalPlantId(),
                route.intent(), query.keywords(), context);
    }
}
