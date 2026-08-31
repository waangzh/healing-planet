package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.config.RagRuntimeConfig;
import com.healingplanet.ai.domain.KnowledgeSource;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Evaluates observable recall gaps; it deliberately does not infer language intent. */
@Component
public class CoverageInspector {

    public RecallCoverage inspect(RetrievalRequest request, List<LogicalEvidenceCandidate> candidates,
                                  RagRuntimeConfig config) {
        List<LogicalEvidenceCandidate> values = candidates == null ? List.of() : candidates;
        Set<KnowledgeSource> requiredSources = requiredSources(request.sourcePlan());
        Set<KnowledgeSource> observedSources = values.stream().map(candidate -> candidate.representative().source())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<KnowledgeSource> coveredSources = intersection(requiredSources, observedSources);

        Set<String> requiredGroups = request.plan().queryGroups().stream()
                .filter(RetrievalQueryGroup::requiredCoverage).map(RetrievalQueryGroup::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> observedGroups = values.stream().flatMap(candidate -> candidate.matchedQueryGroupIds().stream())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> coveredGroups = intersection(requiredGroups, observedGroups);

        Set<String> requiredEntities = requiredEntities(request);
        Set<String> observedEntities = values.stream().map(candidate -> candidate.representative().canonicalPlantId())
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> coveredEntities = intersection(requiredEntities, observedEntities);

        Set<String> requiredTopics = request.plan().searchKnowledge()
                ? new LinkedHashSet<>(request.topicHints()) : Set.of();
        Set<String> observedTopics = values.stream()
                .filter(candidate -> candidate.representative().source() == KnowledgeSource.PLANT)
                .map(candidate -> candidate.representative().knowledgeType())
                .filter(value -> value != null && !value.isBlank()).map(value -> value.toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> normalizedTopics = requiredTopics.stream().map(value -> value.toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> coveredTopics = intersection(normalizedTopics, observedTopics);

        return new RecallCoverage(coveredSources, coveredGroups, coveredEntities, coveredTopics,
                (int) values.stream().map(LogicalEvidenceCandidate::logicalEvidenceId).distinct().count(),
                difference(requiredSources, coveredSources), difference(requiredGroups, coveredGroups),
                difference(requiredEntities, coveredEntities), difference(normalizedTopics, coveredTopics),
                config.adaptiveRecall().minUniqueLogicalCandidates());
    }

    private Set<String> requiredEntities(RetrievalRequest request) {
        Set<String> values = request.plan().queryGroups().stream()
                .flatMap(group -> group.canonicalPlantIds().stream())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!values.isEmpty()) return values;
        if (request.entityResolution() != null && request.entityResolution().canonicalPlantIds().size() > 1) {
            values.addAll(request.entityResolution().canonicalPlantIds());
        }
        return values;
    }

    private Set<KnowledgeSource> requiredSources(SourcePlan sourcePlan) {
        Set<KnowledgeSource> values = new LinkedHashSet<>();
        if (sourcePlan.knowledge().required()) values.add(KnowledgeSource.PLANT);
        if (sourcePlan.community().required()) values.add(KnowledgeSource.COMMUNITY);
        return values;
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
