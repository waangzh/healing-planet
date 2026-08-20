package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.domain.Evidence;
import com.healingplanet.ai.domain.EvidenceType;
import com.healingplanet.ai.domain.KnowledgeDocument;
import com.healingplanet.ai.domain.KnowledgeSource;
import com.healingplanet.ai.domain.RagQuery;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
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

    public List<Evidence> rank(RagQuery query, List<RetrievalCandidate> candidates,
                               Map<String, Double> rerankScores) {
        // Candidate relevance has already been gated by retrieval. Reranking should
        // change priority without applying another score-distribution-dependent cutoff.
        return candidates.stream()
                .map(candidate -> toEvidence(query, candidate, rerankScores.get(candidate.document().id())))
                .sorted((left, right) -> Double.compare(right.finalScore(), left.finalScore()))
                .toList();
    }

    private Evidence toEvidence(RagQuery query, RetrievalCandidate candidate, Double rerankScore) {
        KnowledgeDocument document = candidate.document();
        RagProperties.SourceAwareRanking ranking = properties.getSourceAwareRanking();
        double retrieval = retrievalScore(candidate, ranking);
        double semantic = rerankScore == null ? retrieval : rerankScore;
        double plantMatch = plantMatch(query, document);
        double quality = communityQuality(document);
        double recency = recency(document.createdAt());
        double finalScore;
        if (!ranking.isEnabled()) {
            finalScore = semantic;
        } else if (document.source() == KnowledgeSource.PLANT) {
            finalScore = ranking.getPlantSemanticWeight() * semantic
                    + ranking.getPlantTrustWeight() * document.trustScore()
                    + ranking.getPlantMatchWeight() * plantMatch;
        } else {
            finalScore = ranking.getCommunitySemanticWeight() * semantic
                    + ranking.getCommunityTrustWeight() * document.trustScore()
                    + ranking.getCommunityQualityWeight() * quality
                    + ranking.getCommunityRecencyWeight() * recency
                    + ranking.getCommunityPlantMatchWeight() * plantMatch;
        }
        return new Evidence(
                document.id(), document.source() == KnowledgeSource.PLANT
                    ? EvidenceType.CARE_GUIDE : EvidenceType.COMMUNITY_POST,
                document.sourceId(), document.source().name(), document.title(), document.content(),
                retrieval, rerankScore, document.trustScore(), clamp(finalScore), document.metadata(),
                document.createdAt()
        );
    }

    private double retrievalScore(RetrievalCandidate candidate, RagProperties.SourceAwareRanking ranking) {
        double rrfNormalized = clamp(candidate.fusionScore() * ranking.getRrfNormalizationFactor());
        if (!ranking.isEnabled()) {
            return properties.getRetrievalMode() == RagProperties.RetrievalMode.DENSE_ONLY
                    && candidate.denseScore() != null ? candidate.denseScore() : rrfNormalized;
        }
        return switch (properties.getRetrievalMode()) {
            case DENSE_ONLY -> candidate.denseScore() == null ? rrfNormalized : candidate.denseScore();
            case BM25_ONLY -> rrfNormalized;
            case HYBRID_RRF -> candidate.denseScore() == null ? rrfNormalized
                    : ranking.getDenseWeight() * candidate.denseScore()
                    + ranking.getRrfWeight() * rrfNormalized;
        };
    }

    private double plantMatch(RagQuery query, KnowledgeDocument document) {
        if (query.canonicalPlantId() != null && query.canonicalPlantId().equals(document.canonicalPlantId())) return 1;
        String normalized = query.query().toLowerCase();
        if (document.plantName() != null && !document.plantName().isBlank()
                && normalized.contains(document.plantName().toLowerCase())) return 1;
        return 0;
    }

    double communityQuality(KnowledgeDocument document) {
        if (document.source() != KnowledgeSource.COMMUNITY) return 1;
        RagProperties.SourceAwareRanking ranking = properties.getSourceAwareRanking();
        double engagement = Math.log1p(document.likes() + ranking.getCollectWeight() * document.collects()
                + ranking.getCommentWeight() * document.comments()
                + ranking.getViewWeight() * document.views())
                / Math.log1p(Math.max(1, ranking.getEngagementNormalization()));
        return clamp(ranking.getCommunityEssenceWeight() * (document.essence() ? 1 : 0)
                + ranking.getCommunityEngagementWeight() * engagement);
    }

    double recency(Instant createdAt) {
        if (createdAt == null) return 0;
        long days = Math.max(0, Duration.between(createdAt, Instant.now()).toDays());
        return Math.exp(-days / Math.max(1, properties.getSourceAwareRanking().getRecencyDecayDays()));
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
