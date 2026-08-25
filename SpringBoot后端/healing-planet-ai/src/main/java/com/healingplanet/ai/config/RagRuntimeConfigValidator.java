package com.healingplanet.ai.config;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RagRuntimeConfigValidator {

    public List<String> validate(RagRuntimeConfig config) {
        List<String> errors = new ArrayList<>();
        if (config == null) {
            errors.add("配置不能为空");
            return errors;
        }
        positiveInRange("denseTopK", config.denseTopK(), 1, 100, errors);
        positiveInRange("sparseTopK", config.sparseTopK(), 1, 100, errors);
        positiveInRange("finalTopK", config.finalTopK(), 1, 30, errors);
        if (config.finalTopK() > Math.max(config.denseTopK(), config.sparseTopK())) {
            errors.add("finalTopK 不能大于 denseTopK 和 sparseTopK 的最大值");
        }
        finiteInRange("similarityThreshold", config.similarityThreshold(), 0, 1, errors);
        positiveInRange("rrfK", config.rrfK(), 1, 500, errors);
        if (config.retrievalMode() == null) errors.add("retrievalMode 不能为空");
        if (config.sourceAwareRanking() == null) {
            errors.add("sourceAwareRanking 不能为空");
            return errors;
        }

        RagRuntimeConfig.SourceAwareRanking ranking = config.sourceAwareRanking();
        finiteInRange("rrfNormalizationFactor", ranking.rrfNormalizationFactor(), 1, 100, errors);
        finiteInRange("denseWeight", ranking.denseWeight(), 0, 1, errors);
        finiteInRange("rrfWeight", ranking.rrfWeight(), 0, 1, errors);
        sumToOne("denseWeight + rrfWeight", ranking.denseWeight() + ranking.rrfWeight(), errors);
        finiteInRange("plantSemanticWeight", ranking.plantSemanticWeight(), 0, 1, errors);
        finiteInRange("plantTrustWeight", ranking.plantTrustWeight(), 0, 1, errors);
        finiteInRange("plantMatchWeight", ranking.plantMatchWeight(), 0, 1, errors);
        sumToOne("植物排序权重", ranking.plantSemanticWeight() + ranking.plantTrustWeight()
                + ranking.plantMatchWeight(), errors);
        finiteInRange("communitySemanticWeight", ranking.communitySemanticWeight(), 0, 1, errors);
        finiteInRange("communityTrustWeight", ranking.communityTrustWeight(), 0, 1, errors);
        finiteInRange("communityQualityWeight", ranking.communityQualityWeight(), 0, 1, errors);
        finiteInRange("communityRecencyWeight", ranking.communityRecencyWeight(), 0, 1, errors);
        finiteInRange("communityPlantMatchWeight", ranking.communityPlantMatchWeight(), 0, 1, errors);
        sumToOne("社区排序权重", ranking.communitySemanticWeight() + ranking.communityTrustWeight()
                + ranking.communityQualityWeight() + ranking.communityRecencyWeight()
                + ranking.communityPlantMatchWeight(), errors);
        finiteInRange("communityEssenceWeight", ranking.communityEssenceWeight(), 0, 1, errors);
        finiteInRange("communityEngagementWeight", ranking.communityEngagementWeight(), 0, 1, errors);
        sumToOne("社区质量权重", ranking.communityEssenceWeight() + ranking.communityEngagementWeight(), errors);
        finiteInRange("collectWeight", ranking.collectWeight(), 0, 10, errors);
        finiteInRange("commentWeight", ranking.commentWeight(), 0, 10, errors);
        finiteInRange("viewWeight", ranking.viewWeight(), 0, 10, errors);
        finiteInRange("engagementNormalization", ranking.engagementNormalization(), 1, 100000, errors);
        finiteInRange("recencyDecayDays", ranking.recencyDecayDays(), 1, 3650, errors);
        positiveInRange("mixedSourceCommunityLimit", config.mixedSourceCommunityLimit(), 0, 30, errors);
        if (config.generation() == null) {
            errors.add("generation 不能为空");
        } else {
            if (config.generation().model() == null || config.generation().model().isBlank()
                    || config.generation().model().length() > 200) {
                errors.add("generation.model 必须是 1 到 200 个字符");
            }
            finiteInRange("generation.temperature", config.generation().temperature(), 0, 2, errors);
            positiveInRange("generation.maxTokens", config.generation().maxTokens(), 1, 16384, errors);
        }
        if (config.rerankerClient() == null) {
            errors.add("rerankerClient 不能为空");
        } else {
            if (config.rerankerClient().connectionId() == null || config.rerankerClient().connectionId().isBlank()
                    || config.rerankerClient().connectionId().length() > 100) {
                errors.add("rerankerClient.connectionId 必须是 1 到 100 个字符");
            }
            String path = config.rerankerClient().path();
            if (path == null || !path.startsWith("/") || path.contains("://") || path.length() > 300) {
                errors.add("rerankerClient.path 必须是以 / 开头的相对路径");
            }
            if (config.rerankerClient().model() == null || config.rerankerClient().model().isBlank()
                    || config.rerankerClient().model().length() > 200) {
                errors.add("rerankerClient.model 必须是 1 到 200 个字符");
            }
            positiveInRange("rerankerClient.candidateTopK", config.rerankerClient().candidateTopK(), 0, 100, errors);
        }
        return errors;
    }

    private void positiveInRange(String field, int value, int min, int max, List<String> errors) {
        if (value < min || value > max) errors.add(field + " 必须在 " + min + " 到 " + max + " 之间");
    }

    private void finiteInRange(String field, double value, double min, double max, List<String> errors) {
        if (!Double.isFinite(value) || value < min || value > max) {
            errors.add(field + " 必须在 " + min + " 到 " + max + " 之间");
        }
    }

    private void sumToOne(String field, double value, List<String> errors) {
        if (!Double.isFinite(value) || Math.abs(value - 1d) > 0.001d) {
            errors.add(field + " 之和必须为 1");
        }
    }
}
