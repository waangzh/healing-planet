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
        List<CandidateSnapshot> preSelectionRanked,
        List<SelectionSnapshot> selected,
        List<StageSnapshot> stages,
        AnswerabilitySnapshot answerability
    ) {
    public RetrievalTrace {
        denseTopK = copy(denseTopK);
        sparseTopK = copy(sparseTopK);
        rrfCandidates = copy(rrfCandidates);
        knowledgeTypeFiltered = copy(knowledgeTypeFiltered);
        rerankBefore = copy(rerankBefore);
        rerankAfter = copy(rerankAfter);
        preSelectionRanked = copy(preSelectionRanked);
        selected = copy(selected);
        stages = copy(stages);
    }

    public RetrievalTrace(RoutingSnapshot routing, EntityResolutionDiagnostics entityResolution,
                          List<CandidateSnapshot> denseTopK, List<CandidateSnapshot> sparseTopK,
                          List<CandidateSnapshot> rrfCandidates, List<CandidateSnapshot> knowledgeTypeFiltered,
                          List<CandidateSnapshot> rerankBefore, List<CandidateSnapshot> rerankAfter,
                          List<CandidateSnapshot> preSelectionRanked, List<SelectionSnapshot> selected,
                          List<StageSnapshot> stages) {
        this(routing, entityResolution, denseTopK, sparseTopK, rrfCandidates, knowledgeTypeFiltered, rerankBefore,
                rerankAfter, preSelectionRanked, selected, stages, null);
    }

    public RetrievalTrace withRouting(RoutingSnapshot value, List<StageSnapshot> precedingStages) {
        List<StageSnapshot> combined = new ArrayList<>(copy(precedingStages));
        combined.addAll(stages);
        return new RetrievalTrace(value, entityResolution, denseTopK, sparseTopK, rrfCandidates,
                knowledgeTypeFiltered, rerankBefore, rerankAfter, preSelectionRanked, selected, combined,
                answerability);
    }

    public RetrievalTrace withAnswerability(AnswerabilitySnapshot value) {
        return new RetrievalTrace(routing, entityResolution, denseTopK, sparseTopK, rrfCandidates,
                knowledgeTypeFiltered, rerankBefore, rerankAfter, preSelectionRanked, selected, stages, value);
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    public record RoutingSnapshot(
            int schemaVersion,
            boolean includeKnowledge,
            boolean includeCommunity,
            boolean includeState,
            String inputIntent,
            String resolvedIntent,
            String domain,
            String entityRequirement,
            String stateEvidenceNeed,
            String searchQuery,
            String requiredKnowledgeTypes,
            String knowledgeRequirement,
            String communityRequirement,
            String stateRequirement,
            String stateNeeds,
            String topicHints,
            Double plantDomainConfidence
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

    public record AnswerabilitySnapshot(String result, String reason) { }

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
