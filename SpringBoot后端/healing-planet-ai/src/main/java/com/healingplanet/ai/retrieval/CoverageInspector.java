package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.config.RagRuntimeConfig;
import com.healingplanet.ai.domain.KnowledgeSource;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Evaluates observable recall gaps; it deliberately does not infer language intent. */
@Component
public class CoverageInspector {

    public RecallCoverage inspect(RetrievalRequest request, List<LogicalEvidenceCandidate> candidates,
                                  RagRuntimeConfig config) {
        List<LogicalEvidenceCandidate> values = candidates == null ? List.of() : candidates;
        Set<KnowledgeSource> requiredSources = requiredSources(request.sourcePlan());
        Map<String, QueryGroupCoverage> groups = new LinkedHashMap<>();
        Set<String> coveredGroups = new LinkedHashSet<>();
        Set<String> coveredEntities = new LinkedHashSet<>();
        Set<String> coveredTopics = new LinkedHashSet<>();
        Set<String> missingEntities = new LinkedHashSet<>();
        Set<String> missingTopics = new LinkedHashSet<>();
        Set<String> missingGroups = new LinkedHashSet<>();
        Set<KnowledgeSource> observedRequiredSources = new LinkedHashSet<>();

        for (RetrievalQueryGroup group : request.plan().queryGroups()) {
            if (!group.requiredCoverage()) continue;
            QueryGroupCoverage groupCoverage = inspectGroup(group, request, values, requiredSources);
            groups.put(group.id(), groupCoverage);
            coveredEntities.addAll(groupCoverage.coveredEntities());
            coveredTopics.addAll(groupCoverage.coveredTopics());
            missingEntities.addAll(groupCoverage.missingEntities());
            missingTopics.addAll(groupCoverage.missingTopics());
            observedRequiredSources.addAll(groupCoverage.coveredSources());
            if (groupCoverage.sufficient()) coveredGroups.add(group.id());
            else missingGroups.add(group.id());
        }
        Set<KnowledgeSource> coveredSources = intersection(requiredSources, observedRequiredSources);

        return new RecallCoverage(groups, coveredSources, coveredGroups, coveredEntities, coveredTopics,
                (int) values.stream().map(LogicalEvidenceCandidate::logicalEvidenceId).distinct().count(),
                difference(requiredSources, coveredSources), missingGroups,
                missingEntities, missingTopics,
                config.adaptiveRecall().minUniqueLogicalCandidates());
    }

    private QueryGroupCoverage inspectGroup(RetrievalQueryGroup group, RetrievalRequest request,
                                            List<LogicalEvidenceCandidate> candidates,
                                            Set<KnowledgeSource> requiredSources) {
        List<LogicalEvidenceCandidate> matching = candidates.stream()
                .filter(candidate -> GroupCoverageMatcher.matches(candidate, group)).toList();
        Set<KnowledgeSource> groupRequiredSources = requiredSources.stream()
                .filter(group.sourceScope()::includes)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<KnowledgeSource> observedSources = matching.stream().map(candidate -> candidate.representative().source())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<KnowledgeSource> coveredSources = intersection(groupRequiredSources, observedSources);

        Set<String> requiredEntities = request.plan().searchKnowledge()
                ? new LinkedHashSet<>(group.canonicalPlantIds()) : Set.of();
        Set<String> observedEntities = matching.stream()
                .filter(candidate -> candidate.representative().source() == KnowledgeSource.PLANT)
                .map(candidate -> candidate.representative().canonicalPlantId())
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> coveredEntities = intersection(requiredEntities, observedEntities);

        Set<String> requiredTopics = request.plan().searchKnowledge() ? normalized(group.topicHints()) : Set.of();
        Set<String> observedTopics = matching.stream()
                .filter(candidate -> candidate.representative().source() == KnowledgeSource.PLANT)
                .map(candidate -> candidate.representative().knowledgeType())
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> coveredTopics = intersection(requiredTopics, observedTopics);

        return new QueryGroupCoverage(group.id(), coveredEntities, difference(requiredEntities, coveredEntities),
                coveredTopics, difference(requiredTopics, coveredTopics), coveredSources,
                difference(groupRequiredSources, coveredSources),
                (int) matching.stream().map(LogicalEvidenceCandidate::logicalEvidenceId).distinct().count());
    }

    static Set<KnowledgeSource> requiredSources(SourcePlan sourcePlan) {
        Set<KnowledgeSource> values = new LinkedHashSet<>();
        if (sourcePlan.knowledge().required()) values.add(KnowledgeSource.PLANT);
        if (sourcePlan.community().required()) values.add(KnowledgeSource.COMMUNITY);
        return values;
    }

    private static Set<String> normalized(Set<String> values) {
        return values.stream().filter(value -> value != null && !value.isBlank())
                .map(value -> value.toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private static <T> Set<T> intersection(Set<T> expected, Set<T> actual) {
        Set<T> result = new LinkedHashSet<>(expected);
        result.retainAll(actual);
        return result;
    }

    private static <T> Set<T> difference(Set<T> expected, Set<T> actual) {
        Set<T> result = new LinkedHashSet<>(expected);
        result.removeAll(actual);
        return result;
    }
}
