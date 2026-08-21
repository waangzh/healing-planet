package com.healingplanet.ai.retrieval;

import java.util.Objects;

/**
 * Retrieval-source policy.  This is intentionally independent from the user
 * intent: an ordinary care question may consult community material as a
 * supplement without becoming a community-search intent.
 */
public record SourcePlan(SourceRequirement knowledge, SourceRequirement community, SourceRequirement state) {

    public enum SourceRequirement {
        OFF,
        OPTIONAL,
        REQUIRED;

        public boolean enabled() {
            return this != OFF;
        }

        public boolean required() {
            return this == REQUIRED;
        }
    }

    public SourcePlan {
        knowledge = Objects.requireNonNullElse(knowledge, SourceRequirement.OFF);
        community = Objects.requireNonNullElse(community, SourceRequirement.OFF);
        state = Objects.requireNonNullElse(state, SourceRequirement.OFF);
    }

    public static SourcePlan of(boolean knowledge, boolean community, boolean state) {
        return new SourcePlan(knowledge ? SourceRequirement.OPTIONAL : SourceRequirement.OFF,
                community ? SourceRequirement.OPTIONAL : SourceRequirement.OFF,
                state ? SourceRequirement.REQUIRED : SourceRequirement.OFF);
    }

    public static SourcePlan off() {
        return new SourcePlan(SourceRequirement.OFF, SourceRequirement.OFF, SourceRequirement.OFF);
    }

    public boolean includeKnowledge() {
        return knowledge.enabled();
    }

    public boolean includeCommunity() {
        return community.enabled();
    }

    public boolean includeState() {
        return state.enabled();
    }
}
