package com.healingplanet.ai.retrieval;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlantEntityResolverTest {

    private PlantEntityResolver resolver;

    @BeforeEach
    void setUp() {
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        when(repository.findPlants()).thenReturn(List.of(
                plant("1", "Epipremnum aureum", "绿萝"),
                plant("10", "Aloe vera", "芦荟")
        ));
        resolver = new PlantEntityResolver(repository);
    }

    @Test
    void shouldResolveKnownPlantAndRejectOtherPlantDocuments() {
        var resolution = resolver.resolve(RagQuery.of("绿萝适合什么光照？"));

        assertThat(resolution.kind()).isEqualTo(PlantEntityResolver.ResolutionKind.KNOWN);
        assertThat(resolution.canonicalPlantId()).isEqualTo("1");
        assertThat(resolver.matches(resolution, document("1", "绿萝", "绿萝光照指南"))).isTrue();
        assertThat(resolver.matches(resolution, document("10", "芦荟", "芦荟光照指南"))).isFalse();
    }

    @Test
    void shouldRecognizeUnknownNamedPlantInsteadOfSearchingAllPlants() {
        var resolution = resolver.resolve(RagQuery.of("火星苔藓适合什么光照？"));

        assertThat(resolution.kind()).isEqualTo(PlantEntityResolver.ResolutionKind.UNKNOWN);
    }

    @Test
    void shouldKeepGenericRecommendationQueriesOpen() {
        var resolution = resolver.resolve(RagQuery.of("适合宿舍养的耐阴植物有哪些？"));

        assertThat(resolution.kind()).isEqualTo(PlantEntityResolver.ResolutionKind.GENERIC);
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

    private KnowledgeRepository.PlantRow plant(String id, String scientificName, String commonName) {
        return new KnowledgeRepository.PlantRow(id, scientificName, commonName,
                null, null, null, null, null, null);
    }

    private KnowledgeDocument document(String canonicalPlantId, String plantName, String content) {
        return new KnowledgeDocument("doc", KnowledgeSource.PLANT, canonicalPlantId, content, content,
                canonicalPlantId, plantName, "LIGHT", List.of(), 1, false,
                0, 0, 0, 0, Instant.EPOCH, Map.of());
    }
}
