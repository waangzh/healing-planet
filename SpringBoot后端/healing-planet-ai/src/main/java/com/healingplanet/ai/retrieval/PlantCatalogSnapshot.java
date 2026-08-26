package com.healingplanet.ai.retrieval;

import java.util.List;
import java.util.Map;

record PlantCatalogSnapshot(Map<String, PlantCatalogEntry> byId,
                            Map<String, List<PlantNameBinding>> byNormalizedName,
                            PlantMentionMatcher mentionMatcher,
                            int maxNameLength) {
    static PlantCatalogSnapshot empty() {
        return new PlantCatalogSnapshot(Map.of(), Map.of(), PlantMentionMatcher.empty(), 0);
    }
}
