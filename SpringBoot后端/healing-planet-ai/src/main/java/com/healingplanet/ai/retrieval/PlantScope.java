package com.healingplanet.ai.retrieval;

import java.util.List;

/** Retrieval constraint produced by closed-set plant entity linking. */
public record PlantScope(Kind kind, List<String> canonicalPlantIds) {
    public enum Kind { HARD, SOFT, NONE, CONFLICT }

    public PlantScope {
        canonicalPlantIds = canonicalPlantIds == null ? List.of() : List.copyOf(canonicalPlantIds);
    }

    static PlantScope hard(List<String> ids) { return new PlantScope(Kind.HARD, ids); }
    static PlantScope soft(List<String> ids) { return new PlantScope(Kind.SOFT, ids); }
    static PlantScope none() { return new PlantScope(Kind.NONE, List.of()); }
    static PlantScope conflict(List<String> ids) { return new PlantScope(Kind.CONFLICT, ids); }

    boolean filtersPlantKnowledge() {
        return (kind == Kind.HARD || kind == Kind.SOFT) && !canonicalPlantIds.isEmpty();
    }
}
