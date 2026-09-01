package com.healingplanet.ai.retrieval;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps physical-fragment ordering deterministic while logical evidence remains
 * the unit of fusion, reranking and final selection.
 */
final class FragmentRanking {
    private FragmentRanking() {
    }

    static List<RetrievalFragmentHit> rank(List<RetrievalFragmentHit> fragments, int rrfK,
                                           Map<String, Double> rerankScores) {
        Map<String, FragmentScore> scores = new LinkedHashMap<>();
        for (RetrievalFragmentHit fragment : fragments == null ? List.<RetrievalFragmentHit>of() : fragments) {
            scores.computeIfAbsent(fragment.fragmentId(), ignored -> new FragmentScore(fragment)).consider(fragment);
        }
        return scores.values().stream()
                .sorted(Comparator.comparing((FragmentScore value) -> value.hasRerankScore(rerankScores)).reversed()
                        .thenComparing(Comparator.comparingDouble((FragmentScore value) ->
                                value.rerankScore(rerankScores)).reversed())
                        .thenComparing(Comparator.comparingDouble((FragmentScore value) ->
                                value.retrievalScore(rrfK)).reversed())
                        .thenComparingInt(FragmentScore::bestRank)
                        .thenComparing(value -> value.hit().fragmentId()))
                .map(FragmentScore::hit)
                .toList();
    }

    private static final class FragmentScore {
        private final RetrievalFragmentHit initial;
        private RetrievalFragmentHit best;
        private Integer denseRank;
        private Integer sparseRank;

        private FragmentScore(RetrievalFragmentHit initial) {
            this.initial = initial;
            this.best = initial;
        }

        private void consider(RetrievalFragmentHit value) {
            if (value.path() == RetrievalPath.DENSE && (denseRank == null || value.rank() < denseRank)) {
                denseRank = value.rank();
            }
            if (value.path() == RetrievalPath.SPARSE && (sparseRank == null || value.rank() < sparseRank)) {
                sparseRank = value.rank();
            }
            if (value.rank() < best.rank()
                    || value.rank().equals(best.rank()) && value.path().name().compareTo(best.path().name()) < 0) {
                best = value;
            }
        }

        private boolean hasRerankScore(Map<String, Double> scores) {
            return scores != null && scores.containsKey(initial.fragmentId());
        }

        private double rerankScore(Map<String, Double> scores) {
            Double score = scores == null ? null : scores.get(initial.fragmentId());
            return score == null ? Double.NEGATIVE_INFINITY : score;
        }

        private double retrievalScore(int rrfK) {
            return rrf(denseRank, rrfK) + rrf(sparseRank, rrfK);
        }

        private int bestRank() {
            return Math.min(denseRank == null ? Integer.MAX_VALUE : denseRank,
                    sparseRank == null ? Integer.MAX_VALUE : sparseRank);
        }

        private RetrievalFragmentHit hit() {
            return best == null ? initial : best;
        }

        private double rrf(Integer rank, int rrfK) {
            return rank == null ? 0d : 1d / (Math.max(0, rrfK) + rank);
        }
    }
}
