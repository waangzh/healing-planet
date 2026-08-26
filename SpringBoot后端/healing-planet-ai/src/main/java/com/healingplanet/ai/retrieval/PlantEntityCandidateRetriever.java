package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.config.RagProperties;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One indexed candidate search followed by bounded Top-K string scoring. */
@Component
final class PlantEntityCandidateRetriever {
    private final SparseIndexService sparseIndex;
    private final RagProperties.EntityResolution properties;

    PlantEntityCandidateRetriever(SparseIndexService sparseIndex, RagProperties properties) {
        this.sparseIndex = sparseIndex;
        this.properties = properties.getEntityResolution();
    }

    List<Candidate> retrieve(String normalizedQuery, PlantCatalogSnapshot catalog) {
        if (normalizedQuery == null || normalizedQuery.length() < 2) return List.of();
        int topK = Math.max(1, properties.getCandidateTopK());
        Map<String, Candidate> candidates = new LinkedHashMap<>();
        for (SparseIndexService.SparseHit hit : sparseIndex.searchEntityNames(normalizedQuery, topK)) {
            PlantCatalogEntry entry = catalog.byId().get(hit.document().canonicalPlantId());
            if (entry == null) continue;
            NameScore best = entry.names().stream().map(name -> bestWindowScore(normalizedQuery, name))
                    .max(Comparator.comparingDouble(NameScore::score)).orElse(new NameScore("", 0));
            if (best.score() > 0) candidates.merge(entry.canonicalPlantId(),
                    new Candidate(entry, best.mention(), best.score(), hit.score()), Candidate::higherScore);
        }
        return candidates.values().stream().sorted(Comparator.comparingDouble(Candidate::score).reversed()
                        .thenComparing(Comparator.comparingDouble(Candidate::indexScore).reversed()))
                .limit(topK).toList();
    }

    private NameScore bestWindowScore(String query, String name) {
        if (query.isBlank() || name.isBlank()) return new NameScore("", 0);
        int minLength = Math.max(1, name.length() - 1);
        int maxLength = Math.min(query.length(), name.length() + 1);
        NameScore best = new NameScore("", 0);
        for (int length = minLength; length <= maxLength; length++) {
            for (int start = 0; start + length <= query.length(); start++) {
                String window = query.substring(start, start + length);
                double score = 1d - (double) editDistance(window, name) / Math.max(window.length(), name.length());
                if (score > best.score()) best = new NameScore(window, score);
            }
        }
        return best;
    }

    /** Bounded to names of indexed Top-K candidates; never applied to the full catalog. */
    private int editDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) previous[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int substitution = previous[j - 1] + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(Math.min(previous[j] + 1, current[j - 1] + 1), substitution);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    record Candidate(PlantCatalogEntry entry, String mention, double score, double indexScore) {
        Candidate(PlantCatalogEntry entry, String mention, double score) { this(entry, mention, score, score); }
        private Candidate higherScore(Candidate other) { return score >= other.score ? this : other; }
    }

    private record NameScore(String mention, double score) { }
}
