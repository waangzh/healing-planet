package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.KnowledgeDocument;
import com.healingplanet.ai.domain.KnowledgeSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Hydrates only fast-changing community ranking fields after recall, never before vector or sparse search. */
@Component
final class CommunityRankingFeatureHydrator {
    private static final Logger log = LoggerFactory.getLogger(CommunityRankingFeatureHydrator.class);
    private final CommunityRankingFeatureRepository repository;

    CommunityRankingFeatureHydrator(CommunityRankingFeatureRepository repository) {
        this.repository = repository;
    }

    static CommunityRankingFeatureHydrator noOp() {
        return new CommunityRankingFeatureHydrator(null);
    }

    List<LogicalEvidenceCandidate> hydrate(List<LogicalEvidenceCandidate> candidates) {
        if (repository == null || candidates == null || candidates.isEmpty()) return candidates;
        Set<String> postIds = candidates.stream().map(LogicalEvidenceCandidate::representative)
                .filter(document -> document.source() == KnowledgeSource.COMMUNITY)
                .map(KnowledgeDocument::sourceId).filter(id -> id != null && !id.isBlank())
                .collect(java.util.stream.Collectors.toSet());
        Map<String, CommunityRankingFeatureRepository.CommunityRankingFeatures> features;
        try {
            features = repository.findByPostIds(postIds);
        } catch (RuntimeException exception) {
            log.warn("Unable to hydrate current community ranking features; using retrieval payload", exception);
            return candidates;
        }
        if (features.isEmpty()) return candidates;
        return candidates.stream().map(candidate -> candidate.mapDocuments(document -> {
            var feature = document.source() == KnowledgeSource.COMMUNITY ? features.get(document.sourceId()) : null;
            return feature == null ? document : withFeatures(document, feature);
        })).toList();
    }

    private KnowledgeDocument withFeatures(KnowledgeDocument document,
                                           CommunityRankingFeatureRepository.CommunityRankingFeatures feature) {
        // Mirrors the deterministic community trust policy in KnowledgeDocumentConverter.
        double trustScore = feature.essence() ? 0.75d : 0.5d;
        return new KnowledgeDocument(document.id(), document.source(), document.sourceId(), document.title(),
                document.embeddingText(), document.displayContent(), document.canonicalPlantId(), document.plantName(),
                document.knowledgeType(), document.tags(), trustScore, feature.essence(), feature.likes(),
                feature.collects(), feature.comments(), feature.views(), document.createdAt(), document.attributes());
    }
}
