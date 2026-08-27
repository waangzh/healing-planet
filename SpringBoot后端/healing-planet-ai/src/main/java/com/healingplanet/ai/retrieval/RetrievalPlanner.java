package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.RagQuery;
import com.healingplanet.ai.query.QueryAnalysis;
import com.healingplanet.ai.query.RetrievalConstraints;
import com.healingplanet.ai.query.StateNeed;
import org.springframework.stereotype.Component;


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
                searchState, analysis.stateNeeds(), analysis.topicHints(), query.query());
    }
}
