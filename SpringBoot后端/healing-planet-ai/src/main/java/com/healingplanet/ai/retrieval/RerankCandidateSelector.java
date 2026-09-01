package com.healingplanet.ai.retrieval;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reserves logical-evidence rerank admission for required groups and sources before global fusion fill. */
final class RerankCandidateSelector {

    List<LogicalEvidenceCandidate> select(RetrievalRequest request, List<LogicalEvidenceCandidate> candidates,
                                          int candidateTopK) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        int capacity = candidateTopK > 0 ? candidateTopK : candidates.size();
        if (request == null || capacity >= candidates.size()) return candidates;

        Map<String, RetrievalQueryGroup> groups = SelectionCoverageUnit.indexedGroups(request);
        Set<SelectionCoverageUnit> uncovered = new LinkedHashSet<>(SelectionCoverageUnit.forRerankAdmission(request));
        List<LogicalEvidenceCandidate> selected = new ArrayList<>();
        Set<String> selectedLogicalEvidenceIds = new LinkedHashSet<>();

        while (!uncovered.isEmpty() && selected.size() < capacity) {
            LogicalEvidenceCandidate best = null;
            Set<SelectionCoverageUnit> bestCoverage = Set.of();
            for (LogicalEvidenceCandidate candidate : candidates) {
                if (selectedLogicalEvidenceIds.contains(candidate.logicalEvidenceId())) continue;
                Set<SelectionCoverageUnit> coverage = coveredBy(candidate, uncovered, groups);
                if (coverage.size() > bestCoverage.size()) {
                    best = candidate;
                    bestCoverage = coverage;
                }
            }
            if (best == null || bestCoverage.isEmpty()) break;
            selected.add(best);
            selectedLogicalEvidenceIds.add(best.logicalEvidenceId());
            uncovered.removeAll(bestCoverage);
        }

        for (LogicalEvidenceCandidate candidate : candidates) {
            if (selected.size() >= capacity) break;
            if (selectedLogicalEvidenceIds.add(candidate.logicalEvidenceId())) selected.add(candidate);
        }
        return List.copyOf(selected);
    }

    private Set<SelectionCoverageUnit> coveredBy(LogicalEvidenceCandidate candidate,
                                                  Set<SelectionCoverageUnit> uncovered,
                                                  Map<String, RetrievalQueryGroup> groups) {
        Set<SelectionCoverageUnit> result = new LinkedHashSet<>();
        for (SelectionCoverageUnit unit : uncovered) {
            if (unit.matches(candidate, groups)) result.add(unit);
        }
        return result;
    }
}
