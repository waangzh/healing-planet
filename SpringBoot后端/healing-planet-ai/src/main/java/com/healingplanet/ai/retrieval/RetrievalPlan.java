package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.query.StateNeed;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
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
        String searchQuery,
        List<RetrievalQueryGroup> queryGroups
) {
    public RetrievalPlan {
        sourcePlan = sourcePlan == null ? new SourcePlan(SourcePlan.SourceRequirement.ALLOWED,
                SourcePlan.SourceRequirement.ALLOWED, SourcePlan.SourceRequirement.ALLOWED) : sourcePlan;
        EnumSet<StateNeed> normalizedNeeds = stateNeeds == null || stateNeeds.isEmpty()
                ? EnumSet.noneOf(StateNeed.class) : EnumSet.copyOf(stateNeeds);
        stateNeeds = Collections.unmodifiableSet(normalizedNeeds);
        topicHints = topicHints == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(topicHints));
        searchQuery = searchQuery == null ? "" : searchQuery;
        queryGroups = normalizeGroups(queryGroups, sourcePlan, topicHints, searchQuery);
    }

    /** Keeps existing call sites source-compatible while assigning one primary query group. */
    public RetrievalPlan(SourcePlan sourcePlan, boolean searchKnowledge, boolean searchCommunity, boolean searchState,
                         Set<StateNeed> stateNeeds, Set<String> topicHints, String searchQuery) {
        this(sourcePlan, searchKnowledge, searchCommunity, searchState, stateNeeds, topicHints, searchQuery, List.of());
    }

    public RetrievalPlan withSearchQuery(String value) {
        List<RetrievalQueryGroup> updatedGroups = queryGroups.stream().map(group -> group.withQuery(value)).toList();
        return new RetrievalPlan(sourcePlan, searchKnowledge, searchCommunity, searchState, stateNeeds, topicHints,
                value, updatedGroups);
    }

    private static List<RetrievalQueryGroup> normalizeGroups(List<RetrievalQueryGroup> values, SourcePlan sourcePlan,
                                                               Set<String> topicHints, String searchQuery) {
        if (values == null || values.isEmpty()) {
            return List.of(new RetrievalQueryGroup("Q1", searchQuery, GroupRole.PRIMARY, topicHints, Set.of(),
                    SourceScope.from(sourcePlan), true));
        }
        return List.copyOf(values);
    }
}
