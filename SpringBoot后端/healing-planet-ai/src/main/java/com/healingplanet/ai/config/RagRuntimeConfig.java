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
        int mixedSourceCommunityLimit,
        Answerability answerability,
        Generation generation,
        RerankerClient rerankerClient) {

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
                properties.getEvidenceSelector().getMixedSourceCommunityLimit(),
                new Answerability(properties.getAnswerability().getMinRetrievalRelevance(),
                        properties.getAnswerability().getMinRerankRelevance(),
                        properties.getAnswerability().getMinAlignedSemanticRelevance(),
                        properties.getAnswerability().getMinAlignedFinalRelevance(),
                        properties.getAnswerability().getStrongRecoveryRelevance()),
                new Generation(properties.getGeneration().getModel(), properties.getGeneration().getTemperature(),
                        properties.getGeneration().getMaxTokens()),
                new RerankerClient("default", properties.getReranker().getPath(), properties.getReranker().getModel(),
                        properties.getReranker().getCandidateTopK()));
    }

    public RagRuntimeConfig withRevision(long value) {
        return new RagRuntimeConfig(value, denseTopK, sparseTopK, finalTopK, similarityThreshold, retrievalMode,
                rrfK, rerankerEnabled, sourceAwareRanking, evidenceSelectorEnabled, mixedSourceCommunityLimit,
                answerability, generation, rerankerClient);
    }

    /** 兼容第一阶段已经落库、尚未包含本阶段字段的版本。 */
    public RagRuntimeConfig completeWith(RagRuntimeConfig fallback) {
        return new RagRuntimeConfig(revision, denseTopK, sparseTopK, finalTopK, similarityThreshold, retrievalMode,
                rrfK, rerankerEnabled, sourceAwareRanking, evidenceSelectorEnabled, mixedSourceCommunityLimit,
                answerability == null ? fallback.answerability : answerability,
                generation == null ? fallback.generation : generation,
                rerankerClient == null ? fallback.rerankerClient : rerankerClient);
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

    public record Generation(String model, double temperature, int maxTokens) {
    }

    public record Answerability(
            double minRetrievalRelevance,
            double minRerankRelevance,
            double minAlignedSemanticRelevance,
            double minAlignedFinalRelevance,
            double strongRecoveryRelevance) {
    }

    /** 仅保存部署侧 profile 标识和非敏感调用参数；URL、密钥和探活地址保留在应用配置中。 */
    public record RerankerClient(String connectionId, String path, String model, int candidateTopK) {
    }
}
