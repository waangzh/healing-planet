package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.RagQuery;
import com.healingplanet.ai.query.QueryAnalysis;
import com.healingplanet.ai.query.RetrievalConstraints;
import com.healingplanet.ai.query.StateNeed;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Converts hard constraints and soft hints into broad retrieval work. Semantic
 * hints can add work, ranking preference, or coverage, but cannot forbid a source.
 */
@Component
public class RetrievalPlanner {
    public RetrievalPlan plan(RagQuery query, QueryAnalysis analysis, RetrievalConstraints constraints,
                              PlantEntityResolver.Resolution entityResolution) {
        SourcePlan constrained = constraints.sourcePlan();
        boolean searchState = !analysis.stateNeeds().isEmpty() && constrained.state().enabled();
        SourcePlan sourcePlan = new SourcePlan(constrained.knowledge(), constrained.community(),
                searchState ? SourcePlan.SourceRequirement.REQUIRED : constrained.state());
        return new RetrievalPlan(sourcePlan, constrained.knowledge().enabled(), constrained.community().enabled(),
                searchState, analysis.stateNeeds(), analysis.topicHints(), query.query(),
                queryGroups(query.query(), analysis, entityResolution, sourcePlan));
    }

    private List<RetrievalQueryGroup> queryGroups(String query, QueryAnalysis analysis,
                                                   PlantEntityResolver.Resolution entityResolution,
                                                   SourcePlan sourcePlan) {
        SourceScope scope = SourceScope.from(sourcePlan);
        if (entityResolution == null || entityResolution.canonicalPlantIds().size() < 2) {
            return List.of(new RetrievalQueryGroup("Q1", query, GroupRole.PRIMARY, analysis.topicHints(), Set.of(),
                    scope, true));
        }
        List<RetrievalQueryGroup> groups = new ArrayList<>();
        int index = 1;
        for (String canonicalPlantId : new LinkedHashSet<>(entityResolution.canonicalPlantIds())) {
            groups.add(new RetrievalQueryGroup("Q" + index++, query, GroupRole.ENTITY_FOCUS,
                    analysis.topicHints(), java.util.Set.of(canonicalPlantId), scope, true));
        }
        return List.copyOf(groups);
    }
}
