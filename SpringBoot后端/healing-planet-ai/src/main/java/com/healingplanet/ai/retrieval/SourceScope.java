package com.healingplanet.ai.retrieval;

/** Sources a query group may recall from; state evidence is intentionally outside this scope. */
public record SourceScope(boolean knowledge, boolean community) {
    public static SourceScope from(SourcePlan sourcePlan) {
        return new SourceScope(sourcePlan.includeKnowledge(), sourcePlan.includeCommunity());
    }

    public boolean includes(com.healingplanet.ai.domain.KnowledgeSource source) {
        return source == com.healingplanet.ai.domain.KnowledgeSource.PLANT && knowledge
                || source == com.healingplanet.ai.domain.KnowledgeSource.COMMUNITY && community;
    }
}
