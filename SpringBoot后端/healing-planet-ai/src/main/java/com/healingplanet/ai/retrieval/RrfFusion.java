package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.KnowledgeDocument;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RrfFusion {
    private static final int RRF_K = 60;

    private RrfFusion() { }

    public static List<RetrievalCandidate> fuse(List<DenseHit> denseHits,
                                                List<SparseIndexService.SparseHit> sparseHits) {
        return fuse(denseHits, sparseHits, RRF_K);
    }

    public static List<RetrievalCandidate> fuse(List<DenseHit> denseHits,
                                                List<SparseIndexService.SparseHit> sparseHits,
                                                int rrfK) {
        if (rrfK < 0) throw new IllegalArgumentException("rrfK must be >= 0");
        Map<String, MutableCandidate> candidates = new LinkedHashMap<>();
        for (int i = 0; i < denseHits.size(); i++) {
            DenseHit hit = denseHits.get(i);
            MutableCandidate candidate = candidates.computeIfAbsent(hit.document().id(),
                    ignored -> new MutableCandidate(hit.document()));
            candidate.denseScore = hit.score();
            candidate.denseRank = i + 1;
            candidate.fusion += 1d / (rrfK + i + 1);
        }
        for (int i = 0; i < sparseHits.size(); i++) {
            var hit = sparseHits.get(i);
            MutableCandidate candidate = candidates.computeIfAbsent(hit.document().id(),
                    ignored -> new MutableCandidate(hit.document()));
            candidate.sparseScore = hit.score();
            candidate.sparseRank = i + 1;
            candidate.fusion += 1d / (rrfK + i + 1);
        }
        List<RetrievalCandidate> result = new ArrayList<>();
        candidates.values().forEach(candidate -> result.add(candidate.freeze()));
        result.sort((left, right) -> Double.compare(right.fusionScore(), left.fusionScore()));
        return result;
    }

    public record DenseHit(KnowledgeDocument document, double score) { }

    private static class MutableCandidate {
        private final KnowledgeDocument document;
        private Double denseScore;
        private Double sparseScore;
        private int denseRank;
        private int sparseRank;
        private double fusion;

        private MutableCandidate(KnowledgeDocument document) { this.document = document; }

        private RetrievalCandidate freeze() {
            return new RetrievalCandidate(document, denseScore, sparseScore,
                    denseRank, sparseRank, fusion);
        }
    }
}
