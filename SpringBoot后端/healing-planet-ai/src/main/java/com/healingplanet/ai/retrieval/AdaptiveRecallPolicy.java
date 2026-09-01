package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.config.RagRuntimeConfig;
import com.healingplanet.ai.domain.KnowledgeSource;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/** Bounded, source-aware expansion from the configured first-pass recall budget. */
@Component
public class AdaptiveRecallPolicy {

    public RecallBudget initial(RagRuntimeConfig config) {
        return new RecallBudget(config.denseTopK(), config.sparseTopK(), config.denseTopK(), config.sparseTopK());
    }

    public RecallBudget next(RetrievalRequest request, RecallCoverage coverage, RecallBudget current,
                             RagRuntimeConfig config) {
        if (!config.adaptiveRecall().enabled() || coverage.sufficient()) return current;
        Set<KnowledgeSource> targets = expansionSources(request, coverage);
        return expand(current, targets, config);
    }

    public boolean expanded(RecallBudget before, RecallBudget after) {
        return before.plantDenseTopK() != after.plantDenseTopK()
                || before.plantSparseTopK() != after.plantSparseTopK()
                || before.communityDenseTopK() != after.communityDenseTopK()
                || before.communitySparseTopK() != after.communitySparseTopK();
    }

    public RecallBudget next(QualifiedRecallCoverage coverage, RecallBudget current, RagRuntimeConfig config) {
        if (!config.adaptiveRecall().enabled() || coverage.sufficient()) return current;
        return expand(current, coverage.missingRequiredSources(), config);
    }

    private Set<KnowledgeSource> expansionSources(RetrievalRequest request, RecallCoverage coverage) {
        if (!coverage.missingRequiredSources().isEmpty()) return coverage.missingRequiredSources();
        Set<KnowledgeSource> result = new LinkedHashSet<>();
        for (RetrievalQueryGroup group : request.plan().queryGroups()) {
            if (coverage.missingRequiredQueryGroups().contains(group.id())) {
                if (group.sourceScope().knowledge()) result.add(KnowledgeSource.PLANT);
                if (group.sourceScope().community()) result.add(KnowledgeSource.COMMUNITY);
            }
        }
        if (!coverage.missingEntities().isEmpty() || !coverage.missingTopics().isEmpty()) {
            if (request.plan().searchKnowledge()) result.add(KnowledgeSource.PLANT);
        }
        if (result.isEmpty() && coverage.uniqueLogicalCandidates() < coverage.minimumUniqueLogicalCandidates()) {
            if (request.plan().searchKnowledge()) result.add(KnowledgeSource.PLANT);
            if (request.plan().searchCommunity()) result.add(KnowledgeSource.COMMUNITY);
        }
        return result;
    }

    private RecallBudget expand(RecallBudget current, Set<KnowledgeSource> targets, RagRuntimeConfig config) {
        int plantDense = expand(current.plantDenseTopK(), config.adaptiveRecall().maxDenseTopK(),
                targets.contains(KnowledgeSource.PLANT) && config.retrievalMode().usesDense());
        int plantSparse = expand(current.plantSparseTopK(), config.adaptiveRecall().maxSparseTopK(),
                targets.contains(KnowledgeSource.PLANT) && config.retrievalMode().usesSparse());
        int communityDense = expand(current.communityDenseTopK(), config.adaptiveRecall().maxDenseTopK(),
                targets.contains(KnowledgeSource.COMMUNITY) && config.retrievalMode().usesDense());
        int communitySparse = expand(current.communitySparseTopK(), config.adaptiveRecall().maxSparseTopK(),
                targets.contains(KnowledgeSource.COMMUNITY) && config.retrievalMode().usesSparse());
        return new RecallBudget(plantDense, plantSparse, communityDense, communitySparse);
    }

    private int expand(int current, int maximum, boolean selected) {
        if (!selected || current >= maximum) return current;
        return Math.min(maximum, Math.max(current + 1, current * 2));
    }
}
