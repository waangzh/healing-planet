package com.healingplanet.ai.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.domain.KnowledgeDocument;
import com.healingplanet.ai.domain.KnowledgeSource;
import com.healingplanet.ai.domain.RagQuery;
import com.healingplanet.ai.ingestion.KnowledgeRepository;
import com.healingplanet.ai.ingestion.PlantEntityDocumentConverter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SparseIndexServiceTest {
    @TempDir
    Path dataDirectory;

    private SparseIndexService index;

    @BeforeEach
    void setUp() {
        RagProperties properties = new RagProperties();
        properties.setDataDirectory(dataDirectory);
        index = new SparseIndexService(properties, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        index.close();
    }

    @Test
    void shouldFindTypoCandidateWithSingleEntityNameSearch() {
        PlantEntityDocumentConverter converter = new PlantEntityDocumentConverter();
        index.replaceAll(KnowledgeSource.PLANT_ENTITY, List.of(
                converter.convert(new KnowledgeRepository.PlantEntityRow(
                        "1", "Epipremnum aureum", "绿萝", List.of("黄金葛"))),
                converter.convert(new KnowledgeRepository.PlantEntityRow(
                        "2", "Sansevieria trifasciata", "虎尾兰", List.of("虎皮兰")))
        ));

        var hits = index.searchEntityNames("虎尾蓝多久浇水", 5);

        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).document().canonicalPlantId()).isEqualTo("2");
    }

    @Test
    void shouldKeepFuzzyLookupCompatibleWithEntityDocumentsIndexedBeforeNameFieldMigration() {
        index.replaceAll(KnowledgeSource.PLANT_ENTITY, List.of(
                new KnowledgeDocument("legacy-2", KnowledgeSource.PLANT_ENTITY, "2", "虎尾兰",
                        "植物名称：虎尾兰\n学名：Sansevieria trifasciata", "2", "虎尾兰",
                        "PLANT_ENTITY", List.of(), 1, false, 0, 0, 0, 0, Instant.EPOCH, Map.of())));

        var hits = index.searchEntityNames("虎尾蓝多久浇水", 5);

        assertThat(hits).extracting(hit -> hit.document().canonicalPlantId()).contains("2");
    }

    @Test
    void shouldResolveRealLuceneTypoCandidateAsSoftScope() {
        PlantEntityDocumentConverter converter = new PlantEntityDocumentConverter();
        List<KnowledgeRepository.PlantEntityRow> rows = List.of(
                new KnowledgeRepository.PlantEntityRow("1", "Epipremnum aureum", "绿萝", List.of("黄金葛")),
                new KnowledgeRepository.PlantEntityRow("2", "Sansevieria trifasciata", "虎尾兰", List.of("虎皮兰")));
        index.replaceAll(KnowledgeSource.PLANT_ENTITY, rows.stream().map(converter::convert).toList());
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        when(repository.findPlantEntities()).thenReturn(rows);
        RagProperties properties = new RagProperties();
        PlantEntityResolver resolver = new PlantEntityResolver(new PlantCatalogIndex(repository),
                new PlantEntityCandidateRetriever(index, properties), mock(PlantEntityDisambiguator.class), properties);
        RagQuery query = RagQuery.of("虎尾蓝多久浇水？");

        var resolution = resolver.resolveQuery(query);

        assertThat(resolution.kind()).isEqualTo(PlantEntityResolver.ResolutionKind.KNOWN);
        assertThat(resolution.canonicalPlantId()).isEqualTo("2");
        assertThat(resolution.scope().kind()).isEqualTo(PlantScope.Kind.SOFT);
    }

    @Test
    void shouldApplyCanonicalPlantFilterBeforeSparseTopK() {
        index.replaceAll(KnowledgeSource.PLANT, List.of(
                plantDocument("p1", "1", "绿萝", "散射光养护要求"),
                plantDocument("p2", "2", "虎尾兰", "散射光养护要求")
        ));

        var hits = index.search(KnowledgeSource.PLANT, "散射光养护要求", 1, List.of("2"));

        assertThat(hits).extracting(hit -> hit.document().canonicalPlantId()).containsExactly("2");
    }

    @Test
    void shouldRefreshReusableSearcherAfterUpsert() {
        index.replaceAll(KnowledgeSource.PLANT, List.of(
                plantDocument("p1", "1", "绿萝", "散射光养护要求")));
        assertThat(index.search(KnowledgeSource.PLANT, "耐旱", 5)).isEmpty();

        index.upsert(plantDocument("p2", "2", "虎尾兰", "虎尾兰耐旱"));

        assertThat(index.search(KnowledgeSource.PLANT, "耐旱", 5))
                .extracting(hit -> hit.document().id()).contains("p2");
    }

    @Test
    void shouldStoreTheSameStaticRetrievalMetadataAsTheDensePayload() {
        KnowledgeDocument original = new KnowledgeDocument("community-1", KnowledgeSource.COMMUNITY, "post-1",
                "绿萝黄叶记录", "标题：绿萝黄叶记录\n正文：改善通风后恢复。", "改善通风后恢复。", "1", "绿萝",
                "COMMUNITY_EXPERIENCE", List.of("绿萝"), 0.8, true, 42, 3, 2, 100, Instant.EPOCH,
                Map.of("logicalEvidenceId", "COMMUNITY:post-1", "fragmentId", "COMMUNITY:post-1:0",
                        "payloadHash", "must-not-enter-index-metadata"));

        index.upsert(original);

        KnowledgeDocument restored = index.documentsByIds(KnowledgeSource.COMMUNITY, Set.of("community-1"))
                .get("community-1");
        assertThat(restored.retrievalMetadata()).isEqualTo(original.retrievalMetadata());
        assertThat(restored.attributes()).doesNotContainKey("payloadHash");
    }

    private KnowledgeDocument plantDocument(String id, String plantId, String plantName, String content) {
        return new KnowledgeDocument(id, KnowledgeSource.PLANT, id, plantName, content, plantId, plantName,
                "LIGHT", List.of(), 1, false, 0, 0, 0, 0, Instant.EPOCH, Map.of());
    }
}
