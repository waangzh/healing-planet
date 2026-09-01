package com.healingplanet.ai.ingestion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingTextBuilderTest {

    private final EmbeddingTextBuilder builder = new EmbeddingTextBuilder();

    @Test
    void shouldBuildSourceSpecificSemanticTextWithoutRankingSignals() {
        String plant = builder.plant("绿萝", "Epipremnum aureum", "浇水", "表土干后浇透。");
        String community = builder.community("绿萝黄叶记录", List.of("绿萝", "黄叶"), "绿萝", "改善通风后恢复。");
        String disease = builder.disease("绿萝", "根腐病", List.of("烂根"), "处理方法：剪除腐根。");
        String entity = builder.plantEntity("绿萝", "Epipremnum aureum", List.of("黄金葛"));

        assertThat(plant).contains("植物：绿萝", "学名：Epipremnum aureum", "养护主题：浇水", "表土干后浇透");
        assertThat(community).contains("标题：绿萝黄叶记录", "标签：绿萝、黄叶", "植物：绿萝", "改善通风后恢复")
                .doesNotContain("likes", "trustScore", "essence");
        assertThat(disease).contains("植物：绿萝", "病害：根腐病", "别名：烂根", "处理方法：剪除腐根");
        assertThat(entity).contains("植物名称：绿萝", "学名：Epipremnum aureum", "别名：黄金葛");
    }
}
