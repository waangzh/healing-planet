package com.healingplanet.ai.retrieval;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Applies candidate, per-logical-evidence and total-fragment budgets before an HTTP rerank call. */
final class RerankFragmentSelector {

    List<RetrievalFragmentHit> select(List<LogicalEvidenceCandidate> candidates, int candidateTopK,
                                      int maxFragmentsPerLogicalEvidence, int maxFragmentsTotal, int rrfK) {
        if (candidates == null || candidates.isEmpty() || maxFragmentsPerLogicalEvidence < 1
                || maxFragmentsTotal < 1) {
            return List.of();
        }
        List<LogicalEvidenceCandidate> boundedCandidates = candidateTopK > 0
                ? candidates.stream().limit(candidateTopK).toList() : candidates;
        List<List<RetrievalFragmentHit>> perCandidate = boundedCandidates.stream()
                .map(candidate -> FragmentRanking.rank(candidate.fragments(), rrfK, java.util.Map.of()).stream()
                        .limit(maxFragmentsPerLogicalEvidence).toList())
                .toList();
        List<RetrievalFragmentHit> selected = new ArrayList<>();
        Set<String> seenFragmentIds = new LinkedHashSet<>();
        // Take each candidate's best fragment before taking any second fragment, so a long parent cannot monopolize
        // a smaller total budget.
        for (int position = 0; position < maxFragmentsPerLogicalEvidence && selected.size() < maxFragmentsTotal;
             position++) {
            for (List<RetrievalFragmentHit> fragments : perCandidate) {
                if (selected.size() >= maxFragmentsTotal) break;
                if (position < fragments.size() && seenFragmentIds.add(fragments.get(position).fragmentId())) {
                    selected.add(fragments.get(position));
                }
            }
        }
        return List.copyOf(selected);
    }
}
