package com.healingplanet.ai.retrieval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Merges per-query-group recall without granting a logical evidence extra RRF
 * credit merely because it occurred in more than one deterministic group.
 */
final class LogicalEvidenceCandidateMerger {

    List<LogicalEvidenceCandidate> merge(List<GroupCandidate> groupCandidates) {
        Map<String, MutableCandidate> merged = new LinkedHashMap<>();
        for (GroupCandidate groupCandidate : groupCandidates) {
            merged.computeIfAbsent(groupCandidate.candidate().logicalEvidenceId(), ignored -> new MutableCandidate())
                    .consider(groupCandidate.groupId(), groupCandidate.candidate());
        }
        return merged.values().stream().map(MutableCandidate::freeze)
                .sorted(Comparator.comparingDouble(LogicalEvidenceCandidate::fusionScore).reversed()).toList();
    }

    record GroupCandidate(String groupId, LogicalEvidenceCandidate candidate) { }

    private static final class MutableCandidate {
        private final Map<String, RetrievalFragmentHit> fragments = new LinkedHashMap<>();
        private final Set<String> groupIds = new LinkedHashSet<>();
        private LogicalEvidenceCandidate best;

        private void consider(String groupId, LogicalEvidenceCandidate candidate) {
            if (groupId != null && !groupId.isBlank()) groupIds.add(groupId);
            groupIds.addAll(candidate.matchedQueryGroupIds());
            candidate.fragments().forEach(fragment -> fragments.putIfAbsent(fragment.fragmentId(), fragment));
            if (best == null || candidate.fusionScore() > best.fusionScore()
                    || candidate.fusionScore() == best.fusionScore()
                    && candidate.representative().id().compareTo(best.representative().id()) < 0) {
                best = candidate;
            }
        }

        private LogicalEvidenceCandidate freeze() {
            return new LogicalEvidenceCandidate(best.logicalEvidenceId(), best.representative(),
                    new ArrayList<>(fragments.values()), best.denseRank(), best.sparseRank(), best.denseScore(),
                    best.sparseScore(), best.fusionScore(), groupIds);
        }
    }
}
