package com.healingplanet.ai.query;

import com.healingplanet.ai.domain.QueryIntent;
import com.healingplanet.ai.domain.RagQuery;
import com.healingplanet.ai.retrieval.KnowledgeTopicClassifier;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/** Produces reusable soft hints once per request; it never closes a source. */
@Component
public class QueryAnalyzer {

    public QueryAnalysis analyze(RagQuery query) {
        String text = QueryLexicon.normalize(query.query());
        boolean personal = QueryLexicon.containsAny(text, QueryLexicon.PERSONAL_CONTEXT);
        boolean current = QueryLexicon.containsAny(text, QueryLexicon.CURRENT);
        boolean history = QueryLexicon.containsAny(text, QueryLexicon.HISTORY);
        boolean freshness = QueryLexicon.containsAny(text, QueryLexicon.FRESHNESS);
        boolean wateringDecision = QueryLexicon.containsAny(text, QueryLexicon.WATERING_DECISION);
        boolean stateDecision = QueryLexicon.containsAny(text, QueryLexicon.STATE_DECISION);
        boolean stateSignal = QueryLexicon.containsAny(text, QueryLexicon.STATE_SIGNALS);

        EnumSet<StateNeed> stateNeeds = EnumSet.noneOf(StateNeed.class);
        boolean stateContext = personal || current || stateSignal || query.intent() == QueryIntent.PERSONAL_CARE;
        if (history && stateContext) stateNeeds.add(StateNeed.HISTORY);
        if (freshness && stateContext) stateNeeds.add(StateNeed.FRESHNESS);
        if (stateContext && (wateringDecision || stateDecision)) stateNeeds.add(StateNeed.DECISION_SUPPORT);
        if (stateContext || !stateNeeds.isEmpty()) {
            stateNeeds.add(StateNeed.CURRENT);
        }
        if (query.intent() == QueryIntent.DISEASE_DIAGNOSIS) {
            stateNeeds.add(StateNeed.CURRENT);
            stateNeeds.add(StateNeed.DECISION_SUPPORT);
        }

        QueryIntent intent = query.intent() == QueryIntent.DISEASE_DIAGNOSIS ? QueryIntent.DISEASE_DIAGNOSIS
                : !stateNeeds.isEmpty() ? QueryIntent.PERSONAL_CARE : QueryIntent.GENERAL_CARE;
        boolean plantTerms = QueryLexicon.containsAny(text, QueryLexicon.PLANT_DOMAIN);
        boolean nonPlantTerms = QueryLexicon.containsAny(text, QueryLexicon.CLEAR_NON_PLANT);
        double confidence = query.canonicalPlantId() != null && !query.canonicalPlantId().isBlank() ? 1d
                : plantTerms ? 0.9d : personal || stateSignal ? 0.55d : nonPlantTerms ? 0.02d : 0.25d;
        Set<String> topics = KnowledgeTopicClassifier.classify(text);
        return new QueryAnalysis(intent, stateNeeds, topics, personal, confidence);
    }
}
