package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.Evidence;
import com.healingplanet.ai.domain.EvidenceType;
import com.healingplanet.ai.domain.KnowledgeDocument;
import com.healingplanet.ai.domain.KnowledgeSource;
import com.healingplanet.ai.domain.RagQuery;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class SourceAwareRanker {
    private static final double MIN_FINAL_SCORE = 0.55;
    private static final double RELATIVE_FINAL_SCORE = 0.97;

    public List<Evidence> rank(RagQuery query, List<RetrievalCandidate> candidates,
                               Map<String, Double> rerankScores, int limit) {
        List<Evidence> ranked = candidates.stream()
                .map(candidate -> toEvidence(query, candidate, rerankScores.get(candidate.document().id())))
                .sorted((left, right) -> Double.compare(right.finalScore(), left.finalScore()))
                .toList();
        if (ranked.isEmpty()) return List.of();

        // A plant guide and a community post have different score distributions. Apply
        // the relative cutoff within each source before merging the final result.
        Map<String, Double> topScoreBySource = ranked.stream()
                .collect(Collectors.toMap(this::sourceKey, Evidence::finalScore, Math::max, HashMap::new));
        return ranked.stream()
                .filter(evidence -> evidence.finalScore() >= admissionScore(topScoreBySource.get(sourceKey(evidence))))
                .limit(limit)
                .toList();
    }

    private double admissionScore(Double topScore) {
        return Math.max(MIN_FINAL_SCORE, topScore * RELATIVE_FINAL_SCORE);
    }

    private String sourceKey(Evidence evidence) {
        return evidence.sourceType() == null ? evidence.type().name() : evidence.sourceType();
    }

    private Evidence toEvidence(RagQuery query, RetrievalCandidate candidate, Double rerankScore) {
        KnowledgeDocument document = candidate.document();
        double rrfNormalized = Math.min(1d, candidate.fusionScore() * 31d);
        double retrieval = candidate.denseScore() == null ? rrfNormalized
                : 0.55 * candidate.denseScore() + 0.45 * rrfNormalized;
        double semantic = rerankScore == null ? retrieval : rerankScore;
        double plantMatch = plantMatch(query, document);
        double quality = communityQuality(document);
        double recency = recency(document.createdAt());
        double finalScore = document.source() == KnowledgeSource.PLANT
                ? 0.70 * semantic + 0.20 * document.trustScore() + 0.10 * plantMatch
                : 0.62 * semantic + 0.15 * document.trustScore() + 0.13 * quality
                    + 0.05 * recency + 0.05 * plantMatch;
        return new Evidence(
                document.id(), document.source() == KnowledgeSource.PLANT
                    ? EvidenceType.CARE_GUIDE : EvidenceType.COMMUNITY_POST,
                document.sourceId(), document.source().name(), document.title(), document.content(),
                retrieval, rerankScore, document.trustScore(), clamp(finalScore), document.metadata(),
                document.createdAt()
        );
    }

    private double plantMatch(RagQuery query, KnowledgeDocument document) {
        if (query.canonicalPlantId() != null && query.canonicalPlantId().equals(document.canonicalPlantId())) return 1;
        String normalized = query.query().toLowerCase();
        if (document.plantName() != null && !document.plantName().isBlank()
                && normalized.contains(document.plantName().toLowerCase())) return 1;
        return 0;
    }

    static double communityQuality(KnowledgeDocument document) {
        if (document.source() != KnowledgeSource.COMMUNITY) return 1;
        double engagement = Math.log1p(document.likes() + 2d * document.collects()
                + 1.5d * document.comments() + 0.05d * document.views()) / Math.log(1001);
        return clamp(0.2 * (document.essence() ? 1 : 0) + 0.8 * engagement);
    }

    static double recency(Instant createdAt) {
        if (createdAt == null) return 0;
        long days = Math.max(0, Duration.between(createdAt, Instant.now()).toDays());
        return Math.exp(-days / 365d);
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
