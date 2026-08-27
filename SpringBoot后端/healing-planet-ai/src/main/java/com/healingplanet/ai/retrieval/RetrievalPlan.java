package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.query.StateNeed;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

/** Concrete retrieval work derived from hard constraints plus soft analysis hints. */
public record RetrievalPlan(
        SourcePlan sourcePlan,
        boolean searchKnowledge,
        boolean searchCommunity,
        boolean searchState,
        Set<StateNeed> stateNeeds,
        Set<String> topicHints,
        String searchQuery
) {
    public RetrievalPlan {
        sourcePlan = sourcePlan == null ? new SourcePlan(SourcePlan.SourceRequirement.ALLOWED,
                SourcePlan.SourceRequirement.ALLOWED, SourcePlan.SourceRequirement.ALLOWED) : sourcePlan;
        EnumSet<StateNeed> normalizedNeeds = stateNeeds == null || stateNeeds.isEmpty()
                ? EnumSet.noneOf(StateNeed.class) : EnumSet.copyOf(stateNeeds);
        stateNeeds = Collections.unmodifiableSet(normalizedNeeds);
        topicHints = topicHints == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(topicHints));
        searchQuery = searchQuery == null ? "" : searchQuery;
    }
}
