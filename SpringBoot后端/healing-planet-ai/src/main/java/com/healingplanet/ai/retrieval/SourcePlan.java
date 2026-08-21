package com.healingplanet.ai.retrieval;

import java.util.Objects;

/**
 * Retrieval-source policy.  This is intentionally independent from the user
 * intent: an ordinary care question may consult community material as a
 * supplement without becoming a community-search intent.
 */
public record SourcePlan(Activation knowledge, Activation community, Activation state) {

    public enum Activation {
        OFF,
        FALLBACK,
        PRIMARY,
        REQUIRED;

        public boolean enabled() {
            return this != OFF;
        }

        public boolean required() {
            return this == REQUIRED;
        }
    }

    public SourcePlan {
        knowledge = Objects.requireNonNullElse(knowledge, Activation.OFF);
        community = Objects.requireNonNullElse(community, Activation.OFF);
        state = Objects.requireNonNullElse(state, Activation.OFF);
    }

    public static SourcePlan of(boolean knowledge, boolean community, boolean state) {
        return new SourcePlan(knowledge ? Activation.PRIMARY : Activation.OFF,
                community ? Activation.PRIMARY : Activation.OFF,
                state ? Activation.PRIMARY : Activation.OFF);
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
