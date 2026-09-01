package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.config.RagRuntimeConfig;
import com.healingplanet.ai.domain.KnowledgeSource;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A deliberately lenient, reranker-backed signal for deciding whether further recall is worthwhile.
 * It is not an Answerability threshold and is inactive without actual reranker scores.
 */
@Component
public class RecallQualificationPolicy {

    public QualifiedRecallCoverage inspect(RetrievalRequest request, List<LogicalEvidenceCandidate> candidates,
                                           Map<String, Double> rerankScores, RagRuntimeConfig config) {
        if (!config.recallQualification().enabled() || !config.rerankerEnabled()
                || rerankScores == null || rerankScores.isEmpty()) {
            return new QualifiedRecallCoverage(Map.of());
        }
        Set<KnowledgeSource> requiredSources = CoverageInspector.requiredSources(request.sourcePlan());
        Map<String, Set<KnowledgeSource>> missingByGroup = new LinkedHashMap<>();
        for (RetrievalQueryGroup group : request.plan().queryGroups()) {
            if (!group.requiredCoverage()) continue;
            Set<KnowledgeSource> requiredForGroup = requiredSources.stream().filter(group.sourceScope()::includes)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            Set<KnowledgeSource> qualified = candidates.stream()
                    .filter(candidate -> GroupCoverageMatcher.matches(candidate, group))
                    .filter(candidate -> rerankScores.getOrDefault(candidate.representativeFragmentId(),
                            Double.NEGATIVE_INFINITY) >= config.recallQualification().minimumRerankScore())
                    .map(candidate -> candidate.representative().source())
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            requiredForGroup.removeAll(qualified);
            missingByGroup.put(group.id(), requiredForGroup);
        }
        return new QualifiedRecallCoverage(missingByGroup);
    }
}
