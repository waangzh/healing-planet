package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.KnowledgeDocument;

import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 同一逻辑证据在多个检索路径中命中的 fragment 聚合结果。
 */
record LogicalEvidenceCandidate(
        String logicalEvidenceId,
        KnowledgeDocument representative,
        List<RetrievalFragmentHit> fragments,
        Integer denseRank,
        Integer sparseRank,
        Double denseScore,
        Double sparseScore,
        double fusionScore,
        Set<String> matchedQueryGroupIds
) {
    LogicalEvidenceCandidate {
        if (logicalEvidenceId == null || logicalEvidenceId.isBlank()) {
            throw new IllegalArgumentException("logicalEvidenceId 不能为空");
        }
        if (representative == null) {
            throw new IllegalArgumentException("representative 不能为空");
        }
        fragments = fragments == null ? List.of() : List.copyOf(fragments);
        if (fragments.isEmpty()) {
            throw new IllegalArgumentException("fragments 不能为空");
        }
        matchedQueryGroupIds = immutable(matchedQueryGroupIds);
    }

    LogicalEvidenceCandidate(String logicalEvidenceId, KnowledgeDocument representative,
                             List<RetrievalFragmentHit> fragments, Integer denseRank, Integer sparseRank,
                             Double denseScore, Double sparseScore, double fusionScore) {
        this(logicalEvidenceId, representative, fragments, denseRank, sparseRank, denseScore, sparseScore,
                fusionScore, Set.of());
    }

    LogicalEvidenceCandidate withRerankedRepresentative(Map<String, Double> rerankScores) {
        if (rerankScores == null || rerankScores.isEmpty()) {
            return this;
        }
        RetrievalFragmentHit selected = uniqueFragments().stream()
                .filter(hit -> rerankScores.containsKey(hit.fragmentId()))
                .max(Comparator.comparingDouble((RetrievalFragmentHit hit) -> rerankScores.get(hit.fragmentId()))
                        .thenComparing(hit -> hit.document().id()))
                .orElse(null);
        if (selected == null || selected.document().id().equals(representative.id())) {
            return this;
        }
        return new LogicalEvidenceCandidate(logicalEvidenceId, selected.document(), fragments,
                denseRank, sparseRank, denseScore, sparseScore, fusionScore, matchedQueryGroupIds);
    }

    Map<String, Object> evidenceMetadata() {
        Map<String, Object> metadata = new LinkedHashMap<>(representative.metadata());
        List<String> fragmentIds = uniqueFragments().stream().map(RetrievalFragmentHit::fragmentId).toList();
        metadata.put("logicalEvidenceId", logicalEvidenceId);
        metadata.put("matchedFragmentIds", String.join(",", fragmentIds));
        metadata.put("matchedFragmentCount", fragmentIds.size());
        metadata.put("matchedQueryGroupIds", String.join(",", matchedQueryGroupIds));
        metadata.put("matchedQueryGroupCount", matchedQueryGroupIds.size());
        return Map.copyOf(metadata);
    }

    String representativeFragmentId() {
        return fragments.stream()
                .filter(hit -> hit.document().id().equals(representative.id()))
                .map(RetrievalFragmentHit::fragmentId)
                .findFirst()
                .orElse(representative.id());
    }

    private List<RetrievalFragmentHit> uniqueFragments() {
        Map<String, RetrievalFragmentHit> unique = new LinkedHashMap<>();
        for (RetrievalFragmentHit fragment : fragments) {
            unique.putIfAbsent(fragment.fragmentId(), fragment);
        }
        return List.copyOf(unique.values());
    }

    private static Set<String> immutable(Set<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values != null) values.stream().filter(value -> value != null && !value.isBlank()).forEach(result::add);
        return Collections.unmodifiableSet(result);
    }
}
