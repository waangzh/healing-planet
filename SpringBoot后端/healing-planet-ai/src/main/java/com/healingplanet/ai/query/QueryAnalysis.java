package com.healingplanet.ai.query;

import com.healingplanet.ai.domain.QueryIntent;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Soft semantic observations about a query. None of these values may suppress
 * retrieval by themselves.
 */
public record QueryAnalysis(
        QueryIntent intentHint,
        Set<StateNeed> stateNeeds,
        Set<String> topicHints,
        boolean personalContextHint,
        double plantDomainConfidence
) {
    public QueryAnalysis {
        intentHint = Objects.requireNonNullElse(intentHint, QueryIntent.GENERAL_CARE);
        EnumSet<StateNeed> normalizedNeeds = stateNeeds == null || stateNeeds.isEmpty()
                ? EnumSet.noneOf(StateNeed.class) : EnumSet.copyOf(stateNeeds);
        stateNeeds = Collections.unmodifiableSet(normalizedNeeds);
        LinkedHashSet<String> normalizedTopics = topicHints == null ? new LinkedHashSet<>() : topicHints.stream()
                .filter(Objects::nonNull).map(String::trim).filter(value -> !value.isEmpty())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        topicHints = Collections.unmodifiableSet(normalizedTopics);
        plantDomainConfidence = Math.max(0d, Math.min(1d, plantDomainConfidence));
    }

    public boolean needsState() {
        return !stateNeeds.isEmpty();
    }
}
