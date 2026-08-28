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
        boolean instanceContext = QueryLexicon.containsAny(text, QueryLexicon.PERSONAL_INSTANCE_CONTEXT);
        boolean pronounContext = text.contains("它");
        boolean current = QueryLexicon.containsAny(text, QueryLexicon.CURRENT);
        boolean history = QueryLexicon.containsAny(text, QueryLexicon.HISTORY);
        boolean freshness = QueryLexicon.containsAny(text, QueryLexicon.FRESHNESS);
        boolean wateringDecision = QueryLexicon.containsAny(text, QueryLexicon.WATERING_DECISION);
        boolean stateDecision = QueryLexicon.containsAny(text, QueryLexicon.STATE_DECISION);
        boolean stateSignal = QueryLexicon.containsAny(text, QueryLexicon.STATE_SIGNALS);
        boolean stateMetric = QueryLexicon.containsAny(text, QueryLexicon.STATE_METRICS);
        boolean sensorContext = QueryLexicon.containsAny(text, QueryLexicon.SENSOR_CONTEXT);
        boolean telemetryMeasurementRequest = QueryLexicon.containsAny(
                text, QueryLexicon.TELEMETRY_MEASUREMENT_REQUEST);
        boolean quantityRequest = QueryLexicon.containsAny(text, QueryLexicon.QUANTITY_REQUEST);
        boolean knowledgeRequirement = QueryLexicon.containsAny(text, QueryLexicon.KNOWLEDGE_REQUIREMENT);
        boolean stateAssessment = QueryLexicon.containsAny(text, QueryLexicon.STATE_ASSESSMENT);
        boolean explicitPersonalIntent = query.intent() == QueryIntent.PERSONAL_CARE;

        boolean historyMeasurement = history && stateMetric
                && QueryLexicon.containsAny(text, QueryLexicon.HISTORY_MEASUREMENT);
        boolean currentMeasurement = stateMetric && !knowledgeRequirement
                && (telemetryMeasurementRequest && (current || sensorContext)
                || quantityRequest && (sensorContext || instanceContext)
                || instanceContext && stateAssessment);
        boolean stateDecisionContext = wateringDecision || (stateDecision && (stateSignal || stateMetric));
        boolean contextualDecision = stateDecisionContext
                && (instanceContext || pronounContext || currentMeasurement || historyMeasurement || sensorContext);
        boolean historyContext = history && (historyMeasurement || sensorContext || instanceContext
                || explicitPersonalIntent);
        boolean freshnessContext = freshness && (sensorContext || currentMeasurement || historyMeasurement
                || instanceContext || pronounContext);

        EnumSet<StateNeed> stateNeeds = EnumSet.noneOf(StateNeed.class);
        if (historyContext) stateNeeds.add(StateNeed.HISTORY);
        if (freshnessContext) stateNeeds.add(StateNeed.FRESHNESS);
        if (contextualDecision) stateNeeds.add(StateNeed.DECISION_SUPPORT);
        if ((explicitPersonalIntent && !historyContext) || currentMeasurement || contextualDecision || freshnessContext
                || (instanceContext && current && !historyContext)) {
            stateNeeds.add(StateNeed.CURRENT);
        }
        if (query.intent() == QueryIntent.DISEASE_DIAGNOSIS) {
            stateNeeds.add(StateNeed.CURRENT);
            stateNeeds.add(StateNeed.DECISION_SUPPORT);
        }

        QueryIntent intent = query.intent() == QueryIntent.DISEASE_DIAGNOSIS ? QueryIntent.DISEASE_DIAGNOSIS
                : !stateNeeds.isEmpty() ? QueryIntent.PERSONAL_CARE
                : query.intent() == QueryIntent.COMMUNITY_SEARCH ? QueryIntent.COMMUNITY_SEARCH
                : QueryIntent.GENERAL_CARE;
        boolean plantTerms = QueryLexicon.containsAny(text, QueryLexicon.PLANT_DOMAIN);
        boolean nonPlantTerms = QueryLexicon.containsAny(text, QueryLexicon.CLEAR_NON_PLANT);
        double confidence = query.canonicalPlantId() != null && !query.canonicalPlantId().isBlank() ? 1d
                : plantTerms && !nonPlantTerms ? 0.9d : plantTerms ? 0.45d
                : personal && (stateSignal || sensorContext) ? 0.55d : nonPlantTerms ? 0.02d : 0.25d;
        Set<String> topics = KnowledgeTopicClassifier.classify(text);
        return new QueryAnalysis(intent, stateNeeds, topics, personal, confidence);
    }
}
