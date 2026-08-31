package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.KnowledgeDocument;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RrfFusion {
    private static final int RRF_K = 60;

    private RrfFusion() { }

    public static List<LogicalEvidenceCandidate> fuse(List<DenseHit> denseHits,
                                                      List<SparseIndexService.SparseHit> sparseHits) {
        return fuse(denseHits, sparseHits, RRF_K);
    }

    public static List<LogicalEvidenceCandidate> fuse(List<DenseHit> denseHits,
                                                      List<SparseIndexService.SparseHit> sparseHits,
                                                      int rrfK) {
        if (rrfK < 0) throw new IllegalArgumentException("rrfK must be >= 0");
        Map<String, MutableCandidate> candidates = new LinkedHashMap<>();
        for (int i = 0; i < denseHits.size(); i++) {
            DenseHit hit = denseHits.get(i);
            RetrievalFragmentHit fragment = RetrievalFragmentHit.dense(hit.document(), i + 1, hit.score());
            MutableCandidate candidate = candidates.computeIfAbsent(fragment.logicalEvidenceId(),
                    ignored -> new MutableCandidate(fragment.logicalEvidenceId()));
            candidate.consider(fragment);
        }
        for (int i = 0; i < sparseHits.size(); i++) {
            var hit = sparseHits.get(i);
            RetrievalFragmentHit fragment = RetrievalFragmentHit.sparse(hit.document(), i + 1, hit.score());
            MutableCandidate candidate = candidates.computeIfAbsent(fragment.logicalEvidenceId(),
                    ignored -> new MutableCandidate(fragment.logicalEvidenceId()));
            candidate.consider(fragment);
        }
        List<LogicalEvidenceCandidate> result = new ArrayList<>();
        candidates.values().forEach(candidate -> result.add(candidate.freeze(rrfK)));
        result.sort((left, right) -> Double.compare(right.fusionScore(), left.fusionScore()));
        return result;
    }

    public record DenseHit(KnowledgeDocument document, double score) { }

    private static class MutableCandidate {
        private final String logicalEvidenceId;
        private final List<RetrievalFragmentHit> fragments = new ArrayList<>();
        private Double denseScore;
        private Double sparseScore;
        private Integer denseRank;
        private Integer sparseRank;

        private MutableCandidate(String logicalEvidenceId) {
            this.logicalEvidenceId = logicalEvidenceId;
        }

        private void consider(RetrievalFragmentHit fragment) {
            fragments.add(fragment);
            if (fragment.path() == RetrievalPath.DENSE) {
                if (denseRank == null || fragment.rank() < denseRank) {
                    denseRank = fragment.rank();
                    denseScore = fragment.score();
                }
            } else if (sparseRank == null || fragment.rank() < sparseRank) {
                sparseRank = fragment.rank();
                sparseScore = fragment.score();
            }
        }

        private LogicalEvidenceCandidate freeze(int rrfK) {
            Map<String, FragmentScore> fragmentScores = new LinkedHashMap<>();
            for (RetrievalFragmentHit fragment : fragments) {
                fragmentScores.computeIfAbsent(fragment.fragmentId(), ignored -> new FragmentScore(fragment.document()))
                        .consider(fragment);
            }
            FragmentScore representative = fragmentScores.values().stream()
                    .max(java.util.Comparator.comparingDouble(score -> score.fusion(rrfK)))
                    .orElseThrow();
            double fusion = rrf(denseRank, rrfK) + rrf(sparseRank, rrfK);
            return new LogicalEvidenceCandidate(logicalEvidenceId, representative.document, fragments,
                    denseRank, sparseRank, denseScore, sparseScore, fusion);
        }

        private double rrf(Integer rank, int rrfK) {
            return rank == null ? 0d : 1d / (rrfK + rank);
        }
    }

    private static class FragmentScore {
        private final KnowledgeDocument document;
        private Integer denseRank;
        private Integer sparseRank;

        private FragmentScore(KnowledgeDocument document) {
            this.document = document;
        }

        private void consider(RetrievalFragmentHit hit) {
            if (hit.path() == RetrievalPath.DENSE) {
                if (denseRank == null || hit.rank() < denseRank) denseRank = hit.rank();
            } else if (sparseRank == null || hit.rank() < sparseRank) {
                sparseRank = hit.rank();
            }
        }

        private double fusion(int rrfK) {
            return contribution(denseRank, rrfK) + contribution(sparseRank, rrfK);
        }

        private double contribution(Integer rank, int rrfK) {
            return rank == null ? 0d : 1d / (rrfK + rank);
        }
    }
}
