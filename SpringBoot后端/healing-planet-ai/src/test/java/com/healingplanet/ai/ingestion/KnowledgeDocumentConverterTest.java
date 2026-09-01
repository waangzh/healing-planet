package com.healingplanet.ai.ingestion;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.domain.KnowledgeSource;
import com.healingplanet.ai.retrieval.PlantCatalogIndex;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
            assertThat(document.embeddingText()).contains("绿萝", "浇水", "表土干后浇透");
            assertThat(document.displayContent()).isEqualTo("表土干后浇透");
            assertThat(document.metadata()).containsEntry("displayContent", "表土干后浇透");
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
            assertThat(document.metadata()).containsEntry("logicalEvidenceId", "COMMUNITY:post-1")
                    .containsEntry("fragmentRole", "CONTENT");
            assertThat(document.metadata().get("fragmentId"))
                    .isEqualTo("COMMUNITY:post-1:" + document.metadata().get("fragmentIndex"));
        });
    }

    @Test
    void shouldStoreDeterministicCommunityPlantAffinityWithoutMakingItARetrievalFilter() {
        PlantCatalogIndex catalog = mock(PlantCatalogIndex.class);
        when(catalog.resolveCommunityPlants("绿萝浇水记录", "叶片下垂后浇透。", List.of("绿萝")))
                .thenReturn(new PlantCatalogIndex.CommunityPlantResolution(List.of("plant-1"), "绿萝", 1d));
        KnowledgeDocumentConverter catalogAware = new KnowledgeDocumentConverter(catalog);
        var post = new KnowledgeRepository.PostRow("post-affinity", "绿萝浇水记录", "叶片下垂后浇透。",
                0, 0, 0, 0, false, Instant.parse("2026-01-01T00:00:00Z"), "绿萝");

        var documents = catalogAware.fromPost(post);

        assertThat(documents).singleElement().satisfies(document -> {
            assertThat(document.canonicalPlantId()).isEqualTo("plant-1");
            assertThat(document.metadata()).containsEntry("resolvedPlantIds", "plant-1")
                    .containsEntry("plantEntityConfidence", "1.0");
        });
    }

    @Test
    void shouldPreferHeadingsAndParagraphsThenUseTokenBoundedHardSplitForCommunityContent() {
        String uninterrupted = "连续记录无句号".repeat(1_500);
        var post = new KnowledgeRepository.PostRow("post-2", "绿萝长文", "# 观察记录\n\n"
                + uninterrupted + "\n\n# 调整方案\n\n减少浇水并改善通风。",
                0, 0, 0, 0, false, Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-02-01T00:00:00Z"), "绿萝");

        var documents = converter.fromPost(post);

        assertThat(documents).hasSizeGreaterThan(2).allSatisfy(document -> {
            assertThat(TokenAwareTextChunker.countTokens(document.embeddingText()))
                    .isLessThanOrEqualTo(new ChunkPolicy(new RagProperties()).maxTokens(KnowledgeSource.COMMUNITY));
            assertThat(document.metadata()).containsKeys("chunkIndex", "chunkCount", "section", "contentHash",
                    "sourceUpdatedAt", "indexVersion", "logicalEvidenceId", "fragmentId",
                    "fragmentRole", "fragmentIndex", "fragmentCount", "fragmentSection");
            assertThat(String.valueOf(document.metadata().get("contentHash"))).hasSize(64);
            assertThat(document.metadata().get("sourceUpdatedAt")).isEqualTo("2026-02-01T00:00:00Z");
        });
        assertThat(documents).anySatisfy(document -> assertThat(document.metadata().get("section")).isEqualTo("观察记录"));
        assertThat(documents).anySatisfy(document -> assertThat(document.metadata().get("section")).isEqualTo("调整方案"));
        assertThat(documents).extracting(document -> document.metadata().get("chunkCount"))
                .containsOnly(String.valueOf(documents.size()));
        assertThat(documents).extracting(document -> document.metadata().get("logicalEvidenceId"))
                .containsOnly(documents.get(0).metadata().get("logicalEvidenceId"));
    }

    @Test
    void shouldSplitLongDetailAdviceWithThePlantTokenBudget() {
        String detailAdvice = "第一段综合养护建议。".repeat(150) + "\n\n"
                + "第二段综合养护建议。".repeat(150) + "\n\n"
                + "第三段综合养护建议。".repeat(150);
        var plant = new KnowledgeRepository.PlantRow("plant-1", "Epipremnum aureum", "绿萝",
                "明亮散射光", "表土干后浇透", "15-30℃", "较高湿度", "生长期薄肥", detailAdvice);

        var generalCare = converter.fromPlant(plant).stream()
                .filter(document -> document.knowledgeType().equals("GENERAL_CARE"))
                .toList();

        assertThat(generalCare).hasSizeGreaterThan(1).allSatisfy(document -> {
            assertThat(document.sourceId()).isEqualTo("plant-1");
            assertThat(TokenAwareTextChunker.countTokens(document.embeddingText()))
                    .isLessThanOrEqualTo(new ChunkPolicy(new RagProperties()).maxTokens(KnowledgeSource.PLANT));
            assertThat(document.metadata()).containsKeys("logicalEvidenceId", "fragmentId", "fragmentIndex",
                    "fragmentRole", "fragmentCount", "fragmentSection");
        });
        assertThat(generalCare).extracting(document -> document.metadata().get("logicalEvidenceId"))
                .containsOnly(generalCare.get(0).metadata().get("logicalEvidenceId"));
        assertThat(generalCare).extracting(document -> document.metadata().get("fragmentIndex"))
                .containsExactlyElementsOf(java.util.stream.IntStream.range(0, generalCare.size())
                        .mapToObj(String::valueOf).toList());
        assertThat(generalCare).extracting(document -> document.metadata().get("logicalEvidenceId"))
                .allMatch(id -> String.valueOf(id).startsWith("PLANT:plant-1:GENERAL_CARE"));
    }

    @Test
    void shouldApplyConfiguredTokenBudgetToGeneralCare() {
        RagProperties properties = new RagProperties();
        properties.getIngestion().setPlantGeneralCareMaxTokens(64);
        KnowledgeDocumentConverter configured = new KnowledgeDocumentConverter(null, new ChunkPolicy(properties));
        String detailAdvice = "连续综合养护建议。".repeat(200);
        var plant = new KnowledgeRepository.PlantRow("plant-1", "Epipremnum aureum", "绿萝",
                null, null, null, null, null, detailAdvice);

        var generalCare = configured.fromPlant(plant).stream()
                .filter(document -> document.knowledgeType().equals("GENERAL_CARE")).toList();

        assertThat(generalCare).hasSizeGreaterThan(1).allSatisfy(document ->
                assertThat(TokenAwareTextChunker.countTokens(document.embeddingText())).isLessThanOrEqualTo(64));
    }

    @Test
    void shouldPreserveRangesWhenSplittingDetailAdviceMarkdown() {
        String detailAdvice = """
                # 综合养护

                - 适温：18-28℃
                - 缓苗：2-3 天
                - 空气湿度：40%~60%
                > **建议**：保持通风
                """;
        var plant = new KnowledgeRepository.PlantRow("plant-1", "Epipremnum aureum", "绿萝",
                "明亮散射光", "表土干后浇透", "15-30℃", "较高湿度", "生长期薄肥", detailAdvice);

        var generalCare = converter.fromPlant(plant).stream()
                .filter(document -> document.knowledgeType().equals("GENERAL_CARE"))
                .map(document -> document.content())
                .toList();

        assertThat(generalCare).singleElement().satisfies(content -> {
            assertThat(content).contains("18-28℃", "2-3 天", "40%~60%");
            assertThat(content).contains("建议：保持通风");
            assertThat(content).doesNotContain("- 适温", "> ", "**");
        });
    }

    @Test
    void shouldPreserveRangesWhenSplittingCommunityPostMarkdown() {
        String content = """
                # 夏季养护记录

                - 温度保持在 18-28℃
                - 缓苗需要 2-3 天
                - 空气湿度控制在 40%~60%
                """;
        var post = new KnowledgeRepository.PostRow("post-range", "范围数据", content,
                0, 0, 0, 0, false, Instant.parse("2026-01-01T00:00:00Z"), "绿萝");

        var documents = converter.fromPost(post);

        assertThat(documents).singleElement().satisfies(document -> {
            assertThat(document.content()).contains("18-28℃", "2-3 天", "40%~60%");
            assertThat(document.content()).doesNotContain("# 夏季养护记录", "- 温度保持");
            assertThat(document.metadata().get("section")).isEqualTo("夏季养护记录");
        });
    }
}
