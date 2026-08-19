package com.healingplanet.ai.domain;

import java.util.ArrayList;
import java.util.List;

public record RetrievalTrace(
        RoutingSnapshot routing,
        EntityResolutionDiagnostics entityResolution,
        List<CandidateSnapshot> denseTopK,
        List<CandidateSnapshot> sparseTopK,
        List<CandidateSnapshot> rrfCandidates,
        List<CandidateSnapshot> knowledgeTypeFiltered,
        List<CandidateSnapshot> rerankBefore,
        List<CandidateSnapshot> rerankAfter,
        List<SelectionSnapshot> selected,
        List<StageSnapshot> stages
) {
    public RetrievalTrace {
        denseTopK = copy(denseTopK);
        sparseTopK = copy(sparseTopK);
        rrfCandidates = copy(rrfCandidates);
        knowledgeTypeFiltered = copy(knowledgeTypeFiltered);
        rerankBefore = copy(rerankBefore);
        rerankAfter = copy(rerankAfter);
        selected = copy(selected);
        stages = copy(stages);
    }

    public RetrievalTrace withRouting(RoutingSnapshot value, List<StageSnapshot> precedingStages) {
        List<StageSnapshot> combined = new ArrayList<>(copy(precedingStages));
        combined.addAll(stages);
        return new RetrievalTrace(value, entityResolution, denseTopK, sparseTopK, rrfCandidates,
                knowledgeTypeFiltered, rerankBefore, rerankAfter, selected, combined);
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    public record RoutingSnapshot(
            boolean knowledge,
            boolean community,
            boolean state,
            String intent,
            String stateEvidenceNeed,
            String searchQuery,
            String requiredKnowledgeType
    ) { }

    public record CandidateSnapshot(
            String id,
            String sourceId,
            String sourceType,
            String title,
            String content,
            String canonicalPlantId,
            String knowledgeType,
            String scope,
            int rank,
            Double denseScore,
            Double sparseScore,
            Integer denseRank,
            Integer sparseRank,
            Double rrfScore,
            Double rerankScore,
            Double finalScore
    ) { }

    public record SelectionSnapshot(
            String id,
            int rank,
            String reason,
            Double finalScore
    ) { }

    public record StageSnapshot(
            String stage,
            String source,
            String scope,
            double durationMs,
            String status,
            String errorType,
            String errorMessage
    ) { }
}
