package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.domain.KnowledgeDocument;
import com.healingplanet.ai.domain.KnowledgeSource;
import com.healingplanet.ai.domain.RagQuery;
import com.healingplanet.ai.ingestion.KnowledgeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlantEntityResolverTest {

    private PlantEntityResolver resolver;
    private VectorStore entityStore;

    @BeforeEach
    void setUp() {
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        when(repository.findPlantEntities()).thenReturn(List.of(
                entityPlant("1", "Epipremnum aureum", "绿萝"),
                entityPlant("2", "Sansevieria trifasciata", "虎尾兰"),
                entityPlant("3", "Monstera deliciosa", "龟背竹"),
                entityPlant("10", "Aloe vera", "芦荟"),
                entityPlant("20", "Anthurium andraeanum", "红掌"),
                entityPlant("21", "Spathiphyllum wallisii", "白掌")
        ));
        entityStore = mock(VectorStore.class);
        when(entityStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        resolver = new PlantEntityResolver(repository, entityStore, null, new RagProperties(), null);
    }

    @Test
    void shouldResolveKnownPlantAndRejectOtherPlantDocuments() {
        var resolution = resolver.resolve(RagQuery.of("绿萝适合什么光照？"));

        assertThat(resolution.kind()).isEqualTo(PlantEntityResolver.ResolutionKind.KNOWN);
        assertThat(resolution.canonicalPlantId()).isEqualTo("1");
        assertThat(resolver.resolve(RagQuery.of("绿萝叶子发黄怎么办？")).kind())
                .isEqualTo(PlantEntityResolver.ResolutionKind.KNOWN);
        assertThat(resolver.matches(resolution, document("1", "绿萝", "绿萝光照指南"))).isTrue();
        assertThat(resolver.matches(resolution, document("10", "芦荟", "芦荟光照指南"))).isFalse();
    }

    @Test
    void shouldRecognizeUnknownNamedPlantInsteadOfSearchingAllPlants() {
        when(entityStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                entityHit("10", "芦荟", 0.76), entityHit("1", "绿萝", 0.73)
        ));
        var resolution = resolver.resolve(RagQuery.of("火星苔藓适合什么光照？"));

        assertThat(resolution.kind()).isEqualTo(PlantEntityResolver.ResolutionKind.UNKNOWN);
    }

    @Test
    void shouldResolveSingleCharacterTypoWithCharacterAndVectorEvidence() {
        when(entityStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                entityHit("1", "绿萝", 0.86), entityHit("10", "芦荟", 0.58)
        ));

        var resolution = resolver.resolve(RagQuery.of("绿箩能一直晒大太阳不？"));

        assertThat(resolution.kind()).isEqualTo(PlantEntityResolver.ResolutionKind.KNOWN);
        assertThat(resolution.canonicalPlantId()).isEqualTo("1");
        assertThat(resolution.method()).isEqualTo(PlantEntityResolver.ResolutionMethod.HYBRID);
        assertThat(resolution.scoreMargin()).isGreaterThan(0.08);
    }

    @Test
    void shouldResolveUniqueThreeCharacterTypoWithProtectedPrefix() {
        var tigerTail = resolver.resolve(RagQuery.of("虎尾蓝多久浇一次水？"));
        var monstera = resolver.resolve(RagQuery.of("龟背主应该在什么情况下浇水？"));

        assertThat(tigerTail.kind()).isEqualTo(PlantEntityResolver.ResolutionKind.KNOWN);
        assertThat(tigerTail.canonicalPlantId()).isEqualTo("2");
        assertThat(tigerTail.method()).isEqualTo(PlantEntityResolver.ResolutionMethod.EDIT_DISTANCE);
        assertThat(monstera.kind()).isEqualTo(PlantEntityResolver.ResolutionKind.KNOWN);
        assertThat(monstera.canonicalPlantId()).isEqualTo("3");
    }

    @Test
    void shouldNotTreatUnregisteredAliasAsTypo() {
        var resolution = resolver.resolve(RagQuery.of("虎皮兰需要什么光照？"));

        assertThat(resolution.kind()).isEqualTo(PlantEntityResolver.ResolutionKind.UNKNOWN);
    }

    @Test
    void shouldRejectUnregisteredAliasAcrossWateringFrequencyPhrases() {
        List<String> queries = List.of(
                "黄金葛一周浇几次水？",
                "黄金葛多久补一次水？",
                "一个星期给黄金葛浇几回水？",
                "想问下黄金葛平时该怎么浇？",
                "黄金葛是不是不能老浇水？",
                "黄金葛是什么植物，平时怎么养护？"
        );

        assertThat(queries).allSatisfy(query -> {
            var resolution = resolver.resolve(RagQuery.of(query));
            assertThat(resolution.kind()).isEqualTo(PlantEntityResolver.ResolutionKind.UNKNOWN);
            assertThat(resolution.rejectionReason()).isEqualTo("no_acceptable_entity_candidate");
        });
    }

    @Test
    void shouldResolveAllExactEntitiesInComparison() {
        var resolution = resolver.resolve(RagQuery.of("红掌和白掌的光照要求一样吗？"));

        assertThat(resolution.kind()).isEqualTo(PlantEntityResolver.ResolutionKind.KNOWN);
        assertThat(resolution.canonicalPlantIds()).containsExactlyInAnyOrder("20", "21");
    }

    @Test
    void shouldRejectComparisonWhenAnyMentionIsUnknown() {
        var resolution = resolver.resolve(RagQuery.of("绿萝和常春藤的浇水方法相同吗？"));

        assertThat(resolution.kind()).isEqualTo(PlantEntityResolver.ResolutionKind.UNKNOWN);
        assertThat(resolution.rejectionReason()).isEqualTo("comparison_entity_unresolved");
    }

    @Test
    void exactNameShouldRespectChineseCompoundBoundary() {
        assertThat(resolver.resolve(RagQuery.of("我的绿萝需要什么光照？")).kind())
                .isEqualTo(PlantEntityResolver.ResolutionKind.KNOWN);
        assertThat(resolver.resolve(RagQuery.of("这盆绿萝需要什么光照？")).kind())
                .isEqualTo(PlantEntityResolver.ResolutionKind.KNOWN);

        var compound = resolver.resolve(RagQuery.of("月球绿萝需要浇水吗？"));
        assertThat(compound.kind()).isEqualTo(PlantEntityResolver.ResolutionKind.UNKNOWN);
        assertThat(compound.rejectionReason()).isEqualTo("known_name_embedded_in_unknown_compound");
    }

    @Test
    void shouldRejectCloseVectorCandidatesAsAmbiguous() {
        when(entityStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                entityHit("1", "绿萝", 0.85), entityHit("10", "芦荟", 0.82)
        ));

        var resolution = resolver.resolve(RagQuery.of("某种室内植物适合什么光照？"));

        assertThat(resolution.kind()).isEqualTo(PlantEntityResolver.ResolutionKind.AMBIGUOUS);
        assertThat(resolution.canonicalPlantId()).isEmpty();
    }

    @Test
    void shouldKeepGenericRecommendationQueriesOpen() {
        when(entityStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                entityHit("1", "绿萝", 0.94), entityHit("10", "芦荟", 0.80)
        ));
        assertThat(List.of(
                "适合宿舍养的耐阴植物有哪些？",
                "什么植物比较耐阴？",
                "推荐几种适合办公室的绿植",
                "植物叶子发黄怎么办？"
        )).allSatisfy(query -> assertThat(resolver.resolve(RagQuery.of(query)).kind())
                .isEqualTo(PlantEntityResolver.ResolutionKind.GENERIC));
    }

    @Test
    void shouldNotSearchAllPlantsWhenCareQueryHasNoConfirmedSubject() {
        var resolution = resolver.resolve(RagQuery.of("多久浇一次水？"));

        assertThat(resolution.kind()).isEqualTo(PlantEntityResolver.ResolutionKind.UNKNOWN);
        assertThat(resolution.rejectionReason()).isEqualTo("plant_query_without_confirmed_entity");
    }

    @Test
    void shouldRejectQueriesOutsidePlantCareDomain() {
        var resolution = resolver.resolve(RagQuery.of("量子纠缠是什么？"));

        assertThat(resolution.kind()).isEqualTo(PlantEntityResolver.ResolutionKind.OUT_OF_DOMAIN);
    }

    @Test
    void shouldMatchCommunityContentWithoutCanonicalPlantId() {
        var resolution = resolver.resolve(RagQuery.of("社区里的绿萝养护经验"));
        KnowledgeDocument community = document("", "", "作者记录了绿萝的日常浇水习惯");

        assertThat(resolver.matches(resolution, community)).isTrue();
    }

    private KnowledgeRepository.PlantEntityRow entityPlant(String id, String scientificName, String commonName) {
        return new KnowledgeRepository.PlantEntityRow(id, scientificName, commonName);
    }

    private Document entityHit(String id, String name, double score) {
        return Document.builder().id("entity-" + id).text("植物名称：" + name)
                .metadata("canonicalPlantId", id).score(score).build();
    }

    private KnowledgeDocument document(String canonicalPlantId, String plantName, String content) {
        return new KnowledgeDocument("doc", KnowledgeSource.PLANT, canonicalPlantId, content, content,
                canonicalPlantId, plantName, "LIGHT", List.of(), 1, false,
                0, 0, 0, 0, Instant.EPOCH, Map.of());
    }
}
