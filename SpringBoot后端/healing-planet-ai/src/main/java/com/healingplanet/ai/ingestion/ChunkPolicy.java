package com.healingplanet.ai.ingestion;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.domain.KnowledgeSource;
import org.springframework.stereotype.Component;

/**
 * 定义不同知识来源在写入索引前的 fragment token 预算。
 *
 * <p>预算属于分块契约，而不是 embedding 批处理契约；后者仍由 Spring AI 的
 * {@code embedding-batch-*} 配置控制。</p>
 */
@Component
public class ChunkPolicy {

    private final RagProperties properties;

    public ChunkPolicy(RagProperties properties) {
        this.properties = properties;
    }

    public int maxTokens(KnowledgeSource source) {
        return switch (source) {
            case PLANT -> validate("plant-general-care-max-tokens",
                    properties.getIngestion().getPlantGeneralCareMaxTokens());
            case COMMUNITY -> validate("community-max-tokens",
                    properties.getIngestion().getCommunityMaxTokens());
            case DISEASE -> validate("disease-max-tokens",
                    properties.getIngestion().getDiseaseMaxTokens());
            case PLANT_ENTITY -> throw new IllegalArgumentException("植物实体不参与文本分块");
        };
    }

    private int validate(String property, int value) {
        if (value < 64) {
            throw new IllegalStateException("app.rag.ingestion." + property + " 必须至少为 64");
        }
        return value;
    }
}
