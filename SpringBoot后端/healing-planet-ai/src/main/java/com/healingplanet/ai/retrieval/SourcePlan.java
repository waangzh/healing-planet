package com.healingplanet.ai.retrieval;

import java.util.Objects;

/**
 * Retrieval-source policy.  This is intentionally independent from the user
 * intent: an ordinary care question may consult community material as a
 * supplement without becoming a community-search intent.
 */
public record SourcePlan(SourceRequirement knowledge, SourceRequirement community, SourceRequirement state) {

    public enum SourceRequirement {
        /** Access is denied by an explicit user, permission, or safety constraint. */
        FORBIDDEN,
        /** The source may participate in broad retrieval and later evidence selection. */
        ALLOWED,
        /** The query cannot be answered safely without attempting this source. */
        REQUIRED;

        public boolean enabled() {
            return this != FORBIDDEN;
        }

        public boolean required() {
            return this == REQUIRED;
        }
    }

    public SourcePlan {
        knowledge = Objects.requireNonNullElse(knowledge, SourceRequirement.FORBIDDEN);
        community = Objects.requireNonNullElse(community, SourceRequirement.FORBIDDEN);
        state = Objects.requireNonNullElse(state, SourceRequirement.FORBIDDEN);
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
