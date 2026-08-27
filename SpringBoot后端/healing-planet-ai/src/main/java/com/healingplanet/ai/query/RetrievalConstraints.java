package com.healingplanet.ai.query;

import com.healingplanet.ai.retrieval.SourcePlan;

import java.util.Objects;

/** Hard source-access constraints derived only from explicit requests or policy. */
public record RetrievalConstraints(
        SourcePlan.SourceRequirement knowledge,
        SourcePlan.SourceRequirement community,
        SourcePlan.SourceRequirement state
) {
    public RetrievalConstraints {
        knowledge = Objects.requireNonNullElse(knowledge, SourcePlan.SourceRequirement.ALLOWED);
        community = Objects.requireNonNullElse(community, SourcePlan.SourceRequirement.ALLOWED);
        state = Objects.requireNonNullElse(state, SourcePlan.SourceRequirement.ALLOWED);
    }

    public static RetrievalConstraints defaults() {
        return new RetrievalConstraints(SourcePlan.SourceRequirement.ALLOWED,
                SourcePlan.SourceRequirement.ALLOWED, SourcePlan.SourceRequirement.ALLOWED);
    }

    public SourcePlan sourcePlan() {
        return new SourcePlan(knowledge, community, state);
    }
}
