package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.domain.KnowledgeDocument;
import com.healingplanet.ai.domain.KnowledgeSource;
import com.healingplanet.ai.domain.RagQuery;
import com.healingplanet.ai.ingestion.KnowledgeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlantEntityResolverTest {
    private PlantEntityResolver resolver;
    private PlantEntityDisambiguator disambiguator;
    private SparseIndexService sparseIndex;
    private KnowledgeRepository repository;
    private PlantCatalogIndex catalog;

    @BeforeEach
    void setUp() {
        repository = mock(KnowledgeRepository.class);
        when(repository.findPlantEntities()).thenReturn(List.of(
                plant("1", "Epipremnum aureum", "绿萝", "黄金葛", "金边绿萝"),
                plant("2", "Sansevieria trifasciata", "虎尾兰", "虎皮兰"),
                plant("3", "Monstera deliciosa", "龟背竹"),
                plant("20", "Anthurium andraeanum", "红掌", "万年青"),
                plant("21", "Spathiphyllum wallisii", "白掌", "万年青")
        ));
        sparseIndex = mock(SparseIndexService.class);
        disambiguator = mock(PlantEntityDisambiguator.class);
        catalog = new PlantCatalogIndex(repository);
        resolver = new PlantEntityResolver(catalog,
                new PlantEntityCandidateRetriever(sparseIndex, new RagProperties()), disambiguator);
    }

    @Test
    void shouldResolveCanonicalIdAndExactCommonScientificAndAliasWithoutLlm() {
        assertThat(resolve(new RagQuery("任意问题", null, null, "1", null, List.of(), Map.of())).canonicalPlantId())
                .isEqualTo("1");
        assertThat(resolve(RagQuery.of("我的绿萝需要什么光照？")).method())
                .isEqualTo(PlantEntityResolver.ResolutionMethod.EXACT_NAME);
        assertThat(resolve(RagQuery.of("Epipremnum aureum 的湿度要求")).method())
                .isEqualTo(PlantEntityResolver.ResolutionMethod.SCIENTIFIC_NAME);
        assertThat(resolve(RagQuery.of("黄金葛多久浇水")).method())
                .isEqualTo(PlantEntityResolver.ResolutionMethod.ALIAS);
        verify(disambiguator, never()).disambiguate(any(), any(), any());
    }

    @Test
    void shouldResolveMultipleMentionsAndPreferLongestOverlap() {
        var multi = resolve(RagQuery.of("绿萝和虎尾兰分别需要什么光照？"));
        assertThat(multi.canonicalPlantIds()).containsExactly("1", "2");
        var overlap = resolve(RagQuery.of("金边绿萝怎么养？"));
        assertThat(overlap.canonicalPlantIds()).containsExactly("1");
        assertThat(overlap.names()).contains("金边绿萝");
    }

    @Test
    void shouldUseLlmOnlyForAliasCollision() {
        when(disambiguator.disambiguate(any(), any(), any()))
                .thenReturn(PlantEntityDisambiguator.Decision.known("20", 0.96));
        var resolution = resolve(RagQuery.of("万年青叶片发黄怎么办？"));
        assertThat(resolution.kind()).isEqualTo(PlantEntityResolver.ResolutionKind.KNOWN);
        assertThat(resolution.canonicalPlantId()).isEqualTo("20");
        assertThat(resolution.method()).isEqualTo(PlantEntityResolver.ResolutionMethod.LLM);
        verify(disambiguator).disambiguate(any(), any(), any());
    }

    @Test
    void shouldKeepGenericQueriesUnscopedAndUnknownQueriesAvailableToRetrieval() {
        var generic = resolve(RagQuery.of("什么植物比较耐阴？"));
        assertThat(generic.scope().kind()).isEqualTo(PlantScope.Kind.NONE);
        var unknown = resolve(RagQuery.of("火星苔藓适合什么光照？"));
        assertThat(unknown.kind()).isEqualTo(PlantEntityResolver.ResolutionKind.UNKNOWN);
        assertThat(unknown.scope().kind()).isEqualTo(PlantScope.Kind.NONE);
    }

    @Test
    void shouldUseIndexedFuzzyCandidatesButNeverAutoLinkTwoCharacterNames() {
        when(sparseIndex.searchEntityNames(any(), any(Integer.class))).thenAnswer(invocation -> {
            String window = invocation.getArgument(0, String.class);
            return window.contains("绿箩") ? List.of(new SparseIndexService.SparseHit(entityDocument("1"), 0.91)) : List.of();
        });
        var typo = resolve(RagQuery.of("绿箩需要什么光照？"));
        assertThat(typo.kind()).isEqualTo(PlantEntityResolver.ResolutionKind.KNOWN);
        assertThat(typo.method()).isEqualTo(PlantEntityResolver.ResolutionMethod.FUZZY);
        verify(disambiguator, never()).disambiguate(any(), any(), any());
    }

    @Test
    void shouldExposeConflictWhileContextCanonicalIdRemainsHardScope() {
        var resolution = resolve(new RagQuery("虎尾兰需要什么光照？", null, null, "1", null, List.of(), Map.of()));
        assertThat(resolution.kind()).isEqualTo(PlantEntityResolver.ResolutionKind.CONFLICT);
        assertThat(resolution.scope()).isEqualTo(new PlantScope(PlantScope.Kind.CONFLICT, List.of("1")));
        assertThat(resolution.rejectionReason()).contains("conflicts");
    }

    @Test
    void shouldRebuildAllIndexedSnapshotStructuresOnCatalogRefresh() {
        assertThat(resolve(RagQuery.of("绿萝光照"))).extracting(PlantEntityResolver.Resolution::canonicalPlantId)
                .isEqualTo("1");
        when(repository.findPlantEntities()).thenReturn(List.of(plant("9", "Ficus lyrata", "琴叶榕", "琴叶树")));
        catalog.refresh();

        assertThat(resolve(RagQuery.of("琴叶树适合什么光照？"))).extracting(PlantEntityResolver.Resolution::canonicalPlantId)
                .isEqualTo("9");
        assertThat(resolve(RagQuery.of("绿萝光照")).kind()).isEqualTo(PlantEntityResolver.ResolutionKind.UNKNOWN);
    }

    private PlantEntityResolver.Resolution resolve(RagQuery query) {
        return resolver.resolve(RetrievalRequest.from(query, new QueryRouter().route(query)));
    }

    private KnowledgeRepository.PlantEntityRow plant(String id, String scientific, String common, String... aliases) {
        return new KnowledgeRepository.PlantEntityRow(id, scientific, common, List.of(aliases));
    }

    private KnowledgeDocument entityDocument(String plantId) {
        return new KnowledgeDocument("entity-" + plantId, KnowledgeSource.PLANT_ENTITY, plantId, "绿萝", "绿萝",
                plantId, "绿萝", "PLANT_ENTITY", List.of(), 1, false, 0, 0, 0, 0, Instant.EPOCH, Map.of());
    }
}
