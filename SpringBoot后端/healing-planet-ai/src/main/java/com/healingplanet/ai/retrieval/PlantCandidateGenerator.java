package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.domain.KnowledgeSource;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class PlantCandidateGenerator {
    private final VectorStore entityStore;
    private final SparseIndexService sparseIndex;
    private final RagProperties.EntityResolution properties;
    private final RetrievalMetrics metrics;

    public PlantCandidateGenerator(@Qualifier("plantEntityVectorStore") VectorStore entityStore,
                                   SparseIndexService sparseIndex, RagProperties ragProperties,
                                   RetrievalMetrics metrics) {
        this.entityStore = entityStore;
        this.sparseIndex = sparseIndex;
        this.properties = ragProperties.getEntityResolution();
        this.metrics = metrics;
    }

    List<Candidate> generate(String rawQuery, String normalizedQuery, List<PlantCatalogEntry> entries,
                             Set<String> directMentionIds, boolean searchRetrievers) {
        Map<String, Candidate> candidates = new LinkedHashMap<>();
        for (PlantCatalogEntry entry : entries) {
            if (directMentionIds.contains(entry.canonicalPlantId())) {
                candidate(candidates, entry).markDirectMention();
            }
            if (entry.names().stream().anyMatch(normalizedQuery::contains)) {
                candidate(candidates, entry).substringScore(1.0);
            }
            double characterScore = entry.names().stream()
                    .mapToDouble(name -> bestSubstringSimilarity(normalizedQuery, name)).max().orElse(0);
            if (characterScore > 0) candidate(candidates, entry).characterScore(characterScore);
        }
        if (searchRetrievers) {
            addSparseCandidates(rawQuery, entries, candidates);
            addVectorCandidates(rawQuery, entries, candidates);
        }
        List<Candidate> ranked = candidates.values().stream()
                .sorted(Comparator.comparing(Candidate::directMention).reversed()
                        .thenComparing(Comparator.comparingDouble(Candidate::rankScore).reversed()))
                .limit(candidateLimit())
                .toList();
        if (metrics != null) metrics.recordCandidates("entity_candidates", "plant_entity", ranked.size());
        return ranked;
    }

    private void addSparseCandidates(String query, List<PlantCatalogEntry> entries,
                                     Map<String, Candidate> candidates) {
        if (sparseIndex == null) return;
        try {
            List<SparseIndexService.SparseHit> hits = sparseIndex.search(
                    KnowledgeSource.PLANT_ENTITY, query, properties.getCandidateTopK());
            double topScore = hits.isEmpty() ? 0 : hits.get(0).score();
            Map<String, PlantCatalogEntry> byId = entriesById(entries);
            for (SparseIndexService.SparseHit hit : hits) {
                PlantCatalogEntry entry = byId.get(hit.document().canonicalPlantId());
                if (entry != null) {
                    double normalizedScore = topScore <= 0 ? 0 : Math.min(1, hit.score() / topScore);
                    candidate(candidates, entry).sparseScore(normalizedScore);
                }
            }
        } catch (RuntimeException ignored) {
            // Other candidate sources remain available while the local index is rebuilding.
        }
    }

    private void addVectorCandidates(String query, List<PlantCatalogEntry> entries,
                                     Map<String, Candidate> candidates) {
        if (entityStore == null) return;
        try {
            SearchRequest request = SearchRequest.builder().query(query)
                    .topK(properties.getCandidateTopK()).similarityThreshold(0).build();
            List<org.springframework.ai.document.Document> hits = metrics == null
                    ? entityStore.similaritySearch(request)
                    : metrics.time("embedding", "plant_entity", () -> entityStore.similaritySearch(request));
            Map<String, PlantCatalogEntry> byId = entriesById(entries);
            for (org.springframework.ai.document.Document hit : hits) {
                Object canonicalPlantId = hit.getMetadata().get("canonicalPlantId");
                PlantCatalogEntry entry = canonicalPlantId == null ? null : byId.get(canonicalPlantId.toString());
                if (entry != null) {
                    candidate(candidates, entry).vectorScore(hit.getScore() == null ? 0 : hit.getScore());
                }
            }
        } catch (RuntimeException ignored) {
            // Safe rejection still works when the entity collection is temporarily unavailable.
        }
    }

    private Map<String, PlantCatalogEntry> entriesById(List<PlantCatalogEntry> entries) {
        Map<String, PlantCatalogEntry> result = new LinkedHashMap<>();
        entries.forEach(entry -> result.put(entry.canonicalPlantId(), entry));
        return result;
    }

    private Candidate candidate(Map<String, Candidate> candidates, PlantCatalogEntry entry) {
        return candidates.computeIfAbsent(entry.canonicalPlantId(), ignored -> new Candidate(entry));
    }

    private int candidateLimit() {
        return Math.max(1, Math.max(properties.getCandidateTopK(), properties.getLlmMaxCandidates()));
    }

    private double bestSubstringSimilarity(String query, String name) {
        if (query.isBlank() || name.isBlank()) return 0;
        int minLength = Math.max(1, name.length() - 1);
        int maxLength = Math.min(query.length(), name.length() + 1);
        double best = 0;
        for (int length = minLength; length <= maxLength; length++) {
            for (int start = 0; start + length <= query.length(); start++) {
                String value = query.substring(start, start + length);
                int distance = levenshtein(value, name);
                best = Math.max(best, 1d - (double) distance / Math.max(value.length(), name.length()));
            }
        }
        return best;
    }

    private int levenshtein(String left, String right) {
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

    static final class Candidate {
        private final PlantCatalogEntry entry;
        private double substringScore;
        private double characterScore;
        private double sparseScore;
        private double vectorScore;
        private boolean directMention;

        private Candidate(PlantCatalogEntry entry) { this.entry = entry; }
        PlantCatalogEntry entry() { return entry; }
        double characterScore() { return characterScore; }
        double sparseScore() { return sparseScore; }
        double vectorScore() { return vectorScore; }
        boolean directMention() { return directMention; }
        boolean hasExactCatalogName() { return substringScore > 0; }
        private void substringScore(double value) { substringScore = Math.max(substringScore, value); }
        private void characterScore(double value) { characterScore = Math.max(characterScore, value); }
        private void sparseScore(double value) { sparseScore = Math.max(sparseScore, value); }
        private void vectorScore(double value) { vectorScore = Math.max(vectorScore, value); }
        private void markDirectMention() { directMention = true; }
        private double rankScore() {
            if (vectorScore > 0) {
                return 0.60 * vectorScore + 0.20 * substringScore + 0.15 * characterScore + 0.05 * sparseScore;
            }
            return Math.max(Math.max(substringScore, characterScore), 0.80 * sparseScore);
        }
    }
}
