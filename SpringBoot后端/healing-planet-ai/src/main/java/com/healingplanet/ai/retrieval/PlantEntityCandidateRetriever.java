package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.domain.KnowledgeSource;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Candidate lookup backed by Lucene entity-name fields; it never scans the catalog. */
@Component
final class PlantEntityCandidateRetriever {
    private final SparseIndexService sparseIndex;
    private final RagProperties.EntityResolution properties;

    PlantEntityCandidateRetriever(SparseIndexService sparseIndex, RagProperties properties) {
        this.sparseIndex = sparseIndex;
        this.properties = properties.getEntityResolution();
    }

    List<Candidate> retrieve(String normalizedQuery, PlantCatalogSnapshot catalog) {
        if (normalizedQuery == null || normalizedQuery.length() < 3 || catalog.maxNameLength() < 3) return List.of();
        Map<String, Candidate> candidates = new LinkedHashMap<>();
        int maxWindow = Math.min(Math.max(3, catalog.maxNameLength() + 1), 32);
        for (int length = 3; length <= Math.min(maxWindow, normalizedQuery.length()); length++) {
            for (int start = 0; start + length <= normalizedQuery.length(); start++) {
                String window = normalizedQuery.substring(start, start + length);
                for (SparseIndexService.SparseHit hit : sparseIndex.searchEntityNames(window,
                        Math.max(1, properties.getCandidateTopK()))) {
                    PlantCatalogEntry entry = catalog.byId().get(hit.document().canonicalPlantId());
                    if (entry != null) candidates.merge(entry.canonicalPlantId(),
                            new Candidate(entry, window, hit.score()), Candidate::higherScore);
                }
            }
        }
        return candidates.values().stream().sorted(Comparator.comparingDouble(Candidate::score).reversed())
                .limit(Math.max(1, properties.getCandidateTopK())).toList();
    }

    record Candidate(PlantCatalogEntry entry, String mention, double score) {
        private Candidate higherScore(Candidate other) { return score >= other.score ? this : other; }
    }
}
