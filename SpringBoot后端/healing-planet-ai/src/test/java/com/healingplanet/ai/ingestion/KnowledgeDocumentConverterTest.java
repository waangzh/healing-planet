package com.healingplanet.ai.ingestion;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeDocumentConverterTest {

    private final KnowledgeDocumentConverter converter = new KnowledgeDocumentConverter();

    @Test
    void shouldCreateTopicDocumentsAndStableIds() {
        var plant = new KnowledgeRepository.PlantRow("plant-1", "Epipremnum aureum", "绿萝",
                "明亮散射光", "表土干后浇透", "15-30℃", "较高湿度", "生长期薄肥", "避免积水");

        var first = converter.fromPlant(plant);
        var second = converter.fromPlant(plant);

        assertThat(first).hasSize(6);
        assertThat(first).extracting(document -> document.id())
                .containsExactlyElementsOf(second.stream().map(document -> document.id()).toList());
        assertThat(first).anySatisfy(document -> {
            assertThat(document.knowledgeType()).isEqualTo("WATERING");
            assertThat(document.content()).contains("绿萝", "浇水", "表土干后浇透");
        });
    }

    @Test
    void shouldSplitLongCommunityContentAndPreserveSourceMetadata() {
        String content = "第一段养护经验。".repeat(100) + "\n\n" + "第二段处理记录。".repeat(100);
        var post = new KnowledgeRepository.PostRow("post-1", "绿萝黄叶记录", content,
                12, 4, 3, 100, true, Instant.parse("2026-01-01T00:00:00Z"), "绿萝,黄叶");

        var documents = converter.fromPost(post);

        assertThat(documents).hasSizeGreaterThan(1);
        assertThat(documents).allSatisfy(document -> {
            assertThat(document.sourceId()).isEqualTo("post-1");
            assertThat(document.tags()).containsExactly("绿萝", "黄叶");
            assertThat(document.trustScore()).isEqualTo(0.75);
        });
    }
}
