package com.healingplanet.ai.config;

/**
 * 可在请求期切换的 RAG 配置。记录类型和嵌套记录均不可变，避免请求执行期间读到半更新配置。
 */
public record RagRuntimeConfig(
        long revision,
        int denseTopK,
        int sparseTopK,
        int finalTopK,
        double similarityThreshold,
        RagProperties.RetrievalMode retrievalMode,
        int rrfK,
        boolean rerankerEnabled,
        SourceAwareRanking sourceAwareRanking,
        boolean evidenceSelectorEnabled,
        int mixedSourceCommunityLimit) {

    public static RagRuntimeConfig from(RagProperties properties) {
        RagProperties.SourceAwareRanking ranking = properties.getSourceAwareRanking();
        return new RagRuntimeConfig(
                0,
                properties.getDenseTopK(),
                properties.getSparseTopK(),
                properties.getFinalTopK(),
                properties.getSimilarityThreshold(),
                properties.getRetrievalMode(),
                properties.getRrfK(),
                properties.getReranker().isEnabled(),
                new SourceAwareRanking(
                        ranking.isEnabled(), ranking.getRrfNormalizationFactor(), ranking.getDenseWeight(),
                        ranking.getRrfWeight(), ranking.getPlantSemanticWeight(), ranking.getPlantTrustWeight(),
                        ranking.getPlantMatchWeight(), ranking.getCommunitySemanticWeight(),
                        ranking.getCommunityTrustWeight(), ranking.getCommunityQualityWeight(),
                        ranking.getCommunityRecencyWeight(), ranking.getCommunityPlantMatchWeight(),
                        ranking.getCommunityEssenceWeight(), ranking.getCommunityEngagementWeight(),
                        ranking.getCollectWeight(), ranking.getCommentWeight(), ranking.getViewWeight(),
                        ranking.getEngagementNormalization(), ranking.getRecencyDecayDays()),
                properties.getEvidenceSelector().isEnabled(),
                properties.getEvidenceSelector().getMixedSourceCommunityLimit());
    }

    public RagRuntimeConfig withRevision(long value) {
        return new RagRuntimeConfig(value, denseTopK, sparseTopK, finalTopK, similarityThreshold, retrievalMode,
                rrfK, rerankerEnabled, sourceAwareRanking, evidenceSelectorEnabled, mixedSourceCommunityLimit);
    }

    public record SourceAwareRanking(
            boolean enabled,
            double rrfNormalizationFactor,
            double denseWeight,
            double rrfWeight,
            double plantSemanticWeight,
            double plantTrustWeight,
            double plantMatchWeight,
            double communitySemanticWeight,
            double communityTrustWeight,
            double communityQualityWeight,
            double communityRecencyWeight,
            double communityPlantMatchWeight,
            double communityEssenceWeight,
            double communityEngagementWeight,
            double collectWeight,
            double commentWeight,
            double viewWeight,
            double engagementNormalization,
            double recencyDecayDays) {
    }
}
