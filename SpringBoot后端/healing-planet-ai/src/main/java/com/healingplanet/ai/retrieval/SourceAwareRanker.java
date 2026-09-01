package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.config.RagRuntimeConfig;
import com.healingplanet.ai.domain.Evidence;
import com.healingplanet.ai.domain.EvidenceType;
import com.healingplanet.ai.domain.KnowledgeDocument;
import com.healingplanet.ai.domain.KnowledgeSource;
import com.healingplanet.ai.domain.RagQuery;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SourceAwareRanker {
    private final RagProperties properties;

    public SourceAwareRanker() {
        this(new RagProperties());
    }

    @Autowired
    public SourceAwareRanker(RagProperties properties) {
        this.properties = properties;
    }

    public List<Evidence> rank(RagQuery query, List<LogicalEvidenceCandidate> candidates,
                               Map<String, Double> rerankScores) {
        return rank(query, candidates, rerankScores, RagRuntimeConfig.from(properties));
    }

    public List<Evidence> rank(RagQuery query, List<LogicalEvidenceCandidate> candidates,
                               Map<String, Double> rerankScores, RagRuntimeConfig config) {
        // Candidate relevance has already been gated by retrieval. Reranking should
        // change priority without applying another score-distribution-dependent cutoff.
        return candidates.stream()
                .map(candidate -> toEvidence(query, candidate,
                        rerankScores.get(candidate.representativeFragmentId()), rerankScores, config))
                .sorted((left, right) -> Double.compare(right.finalScore(), left.finalScore()))
                .toList();
    }

    private Evidence toEvidence(RagQuery query, LogicalEvidenceCandidate candidate, Double rerankScore,
                                Map<String, Double> rerankScores, RagRuntimeConfig config) {
        KnowledgeDocument document = candidate.representative();
        RagRuntimeConfig.SourceAwareRanking ranking = config.sourceAwareRanking();
        double retrieval = retrievalScore(candidate, ranking, config.retrievalMode());
        double semantic = rerankScore == null ? retrieval : rerankScore;
        double plantMatch = plantMatch(query, document);
        double quality = communityQuality(document, ranking);
        double recency = recency(document.createdAt(), ranking);
        double finalScore;
        if (!ranking.enabled()) {
            finalScore = semantic;
        } else if (document.source() == KnowledgeSource.PLANT) {
            finalScore = ranking.plantSemanticWeight() * semantic
                    + ranking.plantTrustWeight() * document.trustScore()
                    + ranking.plantMatchWeight() * plantMatch;
        } else {
            finalScore = ranking.communitySemanticWeight() * semantic
                    + ranking.communityTrustWeight() * document.trustScore()
                    + ranking.communityQualityWeight() * quality
                    + ranking.communityRecencyWeight() * recency
                    + ranking.communityPlantMatchWeight() * plantMatch;
        }
        return new Evidence(
                document.id(), document.source() == KnowledgeSource.PLANT
                    ? EvidenceType.CARE_GUIDE : EvidenceType.COMMUNITY_POST,
                document.sourceId(), document.source().name(), document.title(), document.content(),
                retrieval, rerankScore, document.trustScore(), clamp(finalScore),
                evidenceMetadata(candidate, rerankScores, config),
                document.createdAt()
        );
    }

    private Map<String, Object> evidenceMetadata(LogicalEvidenceCandidate candidate,
                                                 Map<String, Double> rerankScores, RagRuntimeConfig config) {
        Map<String, Object> metadata = new LinkedHashMap<>(candidate.evidenceMetadata());
        List<Map<String, String>> contextFragments = candidate.contextFragments(rerankScores,
                        config.contextAssembly().maxFragmentsPerLogicalEvidence(), config.rrfK()).stream()
                .map(fragment -> contextFragment(fragment.document(), fragment.fragmentId())).toList();
        metadata.put("contextFragments", contextFragments);
        metadata.put("contextFragmentCount", contextFragments.size());
        return Map.copyOf(metadata);
    }

    private Map<String, String> contextFragment(KnowledgeDocument document, String fragmentId) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("fragmentId", fragmentId);
        result.put("content", document.content() == null ? "" : document.content());
        result.put("section", document.attributes().getOrDefault("fragmentSection",
                document.attributes().getOrDefault("section", "")));
        result.put("index", document.attributes().getOrDefault("fragmentIndex", "0"));
        return Map.copyOf(result);
    }

    private double retrievalScore(LogicalEvidenceCandidate candidate, RagRuntimeConfig.SourceAwareRanking ranking,
                                  RagProperties.RetrievalMode retrievalMode) {
        double rrfNormalized = clamp(candidate.fusionScore() * ranking.rrfNormalizationFactor());
        if (!ranking.enabled()) {
            return retrievalMode == RagProperties.RetrievalMode.DENSE_ONLY
                    && candidate.denseScore() != null ? candidate.denseScore() : rrfNormalized;
        }
        return switch (retrievalMode) {
            case DENSE_ONLY -> candidate.denseScore() == null ? rrfNormalized : candidate.denseScore();
            case BM25_ONLY -> rrfNormalized;
            case HYBRID_RRF -> candidate.denseScore() == null ? rrfNormalized
                    : ranking.denseWeight() * candidate.denseScore()
                    + ranking.rrfWeight() * rrfNormalized;
        };
    }

    private double plantMatch(RagQuery query, KnowledgeDocument document) {
        if (query.canonicalPlantId() != null && query.canonicalPlantId().equals(document.canonicalPlantId())) return 1;
        String normalized = query.query().toLowerCase();
        if (document.plantName() != null && !document.plantName().isBlank()
                && normalized.contains(document.plantName().toLowerCase())) return 1;
        return 0;
    }

    double communityQuality(KnowledgeDocument document, RagRuntimeConfig.SourceAwareRanking ranking) {
        if (document.source() != KnowledgeSource.COMMUNITY) return 1;
        double engagement = Math.log1p(document.likes() + ranking.collectWeight() * document.collects()
                + ranking.commentWeight() * document.comments()
                + ranking.viewWeight() * document.views())
                / Math.log1p(Math.max(1, ranking.engagementNormalization()));
        return clamp(ranking.communityEssenceWeight() * (document.essence() ? 1 : 0)
                + ranking.communityEngagementWeight() * engagement);
    }

    double recency(Instant createdAt, RagRuntimeConfig.SourceAwareRanking ranking) {
        if (createdAt == null) return 0;
        long days = Math.max(0, Duration.between(createdAt, Instant.now()).toDays());
        return Math.exp(-days / Math.max(1, ranking.recencyDecayDays()));
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
