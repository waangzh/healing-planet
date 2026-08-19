package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.EntityResolutionDiagnostics;
import com.healingplanet.ai.domain.Evidence;
import com.healingplanet.ai.domain.KnowledgeDocument;
import com.healingplanet.ai.domain.RetrievalTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

final class RetrievalTraceCollector {

    private static final Logger log = LoggerFactory.getLogger(RetrievalTraceCollector.class);

    private final boolean enabled;
    private final List<RetrievalTrace.CandidateSnapshot> dense = new ArrayList<>();
    private final List<RetrievalTrace.CandidateSnapshot> sparse = new ArrayList<>();
    private final List<RetrievalTrace.CandidateSnapshot> rrf = new ArrayList<>();
    private final List<RetrievalTrace.CandidateSnapshot> filtered = new ArrayList<>();
    private final List<RetrievalTrace.CandidateSnapshot> rerankBefore = new ArrayList<>();
    private final List<RetrievalTrace.CandidateSnapshot> rerankAfter = new ArrayList<>();
    private final List<RetrievalTrace.SelectionSnapshot> selected = new ArrayList<>();
    private final List<RetrievalTrace.StageSnapshot> stages = new ArrayList<>();

    RetrievalTraceCollector(boolean enabled) {
        this.enabled = enabled;
    }

    <T> T time(String stage, String source, String scope, Supplier<T> operation) {
        if (!enabled) return operation.get();
        long started = System.nanoTime();
        try {
            T result = operation.get();
            stages.add(stage(stage, source, scope, started, "ok", null));
            return result;
        } catch (RuntimeException exception) {
            RetrievalTrace.StageSnapshot failure = stage(stage, source, scope, started, "error", exception);
            stages.add(failure);
            log.error("Eval retrieval stage failed: stage={} source={} scope={} durationMs={} errorType={} message={}",
                    stage, source, scope, failure.durationMs(), failure.errorType(), failure.errorMessage());
            throw exception;
        }
    }

    void dense(List<RrfFusion.DenseHit> hits, String scope) {
        if (!enabled) return;
        for (int i = 0; i < hits.size(); i++) {
            RrfFusion.DenseHit hit = hits.get(i);
            dense.add(candidate(hit.document(), scope, i + 1, hit.score(), null,
                    i + 1, null, null, null, null));
        }
    }

    void sparse(List<SparseIndexService.SparseHit> hits, String scope) {
        if (!enabled) return;
        for (int i = 0; i < hits.size(); i++) {
            SparseIndexService.SparseHit hit = hits.get(i);
            sparse.add(candidate(hit.document(), scope, i + 1, null, hit.score(),
                    null, i + 1, null, null, null));
        }
    }

    void rrf(List<RetrievalCandidate> candidates, String scope) {
        addCandidates(rrf, candidates, scope, null, false);
    }

    void filtered(List<RetrievalCandidate> candidates) {
        addCandidates(filtered, candidates, "all", null, false);
    }

    void rerank(List<RetrievalCandidate> before, List<RetrievalCandidate> after,
                java.util.Map<String, Double> scores) {
        addCandidates(rerankBefore, before, "all", scores, false);
        addCandidates(rerankAfter, after, "all", scores, false);
    }

    void selected(List<Evidence> evidence, java.util.Map<String, String> reasons) {
        if (!enabled) return;
        for (int i = 0; i < evidence.size(); i++) {
            Evidence item = evidence.get(i);
            selected.add(new RetrievalTrace.SelectionSnapshot(item.id(), i + 1,
                    reasons.getOrDefault(item.id(), "GLOBAL_RANKING"), item.finalScore()));
        }
    }

    List<RetrievalTrace.StageSnapshot> stages() {
        return List.copyOf(stages);
    }

    RetrievalTrace build(EntityResolutionDiagnostics entityResolution) {
        if (!enabled) return null;
        return new RetrievalTrace(null, entityResolution, dense, sparse, rrf, filtered,
                rerankBefore, rerankAfter, selected, stages);
    }

    private void addCandidates(List<RetrievalTrace.CandidateSnapshot> target,
                               List<RetrievalCandidate> candidates, String scope,
                               java.util.Map<String, Double> rerankScores, boolean includeFinalScore) {
        if (!enabled) return;
        for (int i = 0; i < candidates.size(); i++) {
            RetrievalCandidate item = candidates.get(i);
            Double rerankScore = rerankScores == null ? null : rerankScores.get(item.document().id());
            target.add(candidate(item.document(), scope, i + 1, item.denseScore(), item.sparseScore(),
                    rank(item.denseRank()), rank(item.sparseRank()), item.fusionScore(), rerankScore,
                    includeFinalScore ? rerankScore : null));
        }
    }

    private Integer rank(int value) {
        return value == 0 ? null : value;
    }

    private RetrievalTrace.CandidateSnapshot candidate(KnowledgeDocument document, String scope, int rank,
                                                        Double denseScore, Double sparseScore,
                                                        Integer denseRank, Integer sparseRank, Double rrfScore,
                                                        Double rerankScore, Double finalScore) {
        return new RetrievalTrace.CandidateSnapshot(document.id(), document.sourceId(), document.source().name(),
                document.title(), document.content(), document.canonicalPlantId(), document.knowledgeType(), scope,
                rank, denseScore, sparseScore, denseRank, sparseRank, rrfScore, rerankScore, finalScore);
    }

    private RetrievalTrace.StageSnapshot stage(String stage, String source, String scope, long started,
                                                 String status, RuntimeException error) {
        double durationMs = Math.round((System.nanoTime() - started) / 1_000d) / 1_000d;
        String message = error == null || error.getMessage() == null ? null : error.getMessage();
        if (message != null && message.length() > 500) message = message.substring(0, 500);
        return new RetrievalTrace.StageSnapshot(stage, source, scope, durationMs, status,
                error == null ? null : error.getClass().getName(), message);
    }
}
