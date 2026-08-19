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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlantEntityResolverTest {

    private PlantEntityResolver resolver;
    private VectorStore entityStore;
    private PlantEntityDisambiguator disambiguator;

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
        disambiguator = mock(PlantEntityDisambiguator.class);
        resolver = new PlantEntityResolver(mockRepository(), entityStore, null, new RagProperties(), null, disambiguator);
        var resolution = resolver.resolve(RagQuery.of("绿萝"));

        assertThat(resolution.kind()).isEqualTo(PlantEntityResolver.ResolutionKind.KNOWN);
        assertThat(resolution.canonicalPlantId()).isEqualTo("1");
        assertThat(resolution.method()).isEqualTo(PlantEntityResolver.ResolutionMethod.EXACT_NAME);
        verify(disambiguator, never()).disambiguate(any(), any(), any());
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
    void shouldResolveConfiguredSingleCharacterTypoWithoutLlm() {
        useLlmDecision(PlantEntityDisambiguator.Decision.known("1", 0.95));
        when(entityStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                entityHit("1", "绿萝", 0.86), entityHit("10", "芦荟", 0.58)
        ));

        var resolution = resolver.resolve(RagQuery.of("绿箩能一直晒大太阳不？"));

        assertThat(resolution.kind()).isEqualTo(PlantEntityResolver.ResolutionKind.KNOWN);
        assertThat(resolution.canonicalPlantId()).isEqualTo("1");
        assertThat(resolution.method()).isEqualTo(PlantEntityResolver.ResolutionMethod.ALIAS);
        verify(disambiguator, never()).disambiguate(any(), any(), any());
    }

    @Test
    void shouldUseLlmFallbackForUncertainMentionWithConstrainedCandidates() {
        PlantEntityDisambiguator disambiguator = mock(PlantEntityDisambiguator.class);
        resolver = new PlantEntityResolver(mockRepository(), entityStore, null, new RagProperties(), null, disambiguator);
        when(entityStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                entityHit("1", "绿萝", 0.62), entityHit("10", "芦荟", 0.41)
        ));
        when(disambiguator.disambiguate(any(), any(), any())).thenReturn(
                PlantEntityDisambiguator.Decision.known("1", 0.93));

        var resolution = resolver.resolve(RagQuery.of("小绿箩耐阴吗？"));

        assertThat(resolution.kind()).isEqualTo(PlantEntityResolver.ResolutionKind.KNOWN);
        assertThat(resolution.canonicalPlantId()).isEqualTo("1");
        assertThat(resolution.method()).isEqualTo(PlantEntityResolver.ResolutionMethod.LLM);
        verify(disambiguator).disambiguate(any(), any(), any());
    }

    @Test
    void shouldNotMapGenericCategoryWordsToSpecificPlantThroughLlm() {
        useLlmDecision(PlantEntityDisambiguator.Decision.unknown("llm_rejected_or_unknown"));
        when(entityStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                entityHit("1", "绿萝", 0.67), entityHit("10", "芦荟", 0.52)
        ));

        var resolution = resolver.resolve(RagQuery.of("绿植耐阴吗？"));

        assertThat(resolution.kind()).isNotEqualTo(PlantEntityResolver.ResolutionKind.KNOWN);
        verify(disambiguator).disambiguate(any(), any(), any());
    }

    @Test
    void shouldResolveConfiguredThreeCharacterTyposWithoutLlm() {
        disambiguator = mock(PlantEntityDisambiguator.class);
        when(disambiguator.disambiguate(any(), any(), any())).thenReturn(
                PlantEntityDisambiguator.Decision.known("2", 0.95),
                PlantEntityDisambiguator.Decision.known("3", 0.95));
        resolver = new PlantEntityResolver(mockRepository(), entityStore, null, new RagProperties(), null, disambiguator);
        var tigerTail = resolver.resolve(RagQuery.of("虎尾蓝多久浇一次水？"));
        var monstera = resolver.resolve(RagQuery.of("龟背主应该在什么情况下浇水？"));

        assertThat(tigerTail.kind()).isEqualTo(PlantEntityResolver.ResolutionKind.KNOWN);
        assertThat(tigerTail.canonicalPlantId()).isEqualTo("2");
        assertThat(tigerTail.method()).isEqualTo(PlantEntityResolver.ResolutionMethod.ALIAS);
        assertThat(monstera.method()).isEqualTo(PlantEntityResolver.ResolutionMethod.ALIAS);
        assertThat(monstera.kind()).isEqualTo(PlantEntityResolver.ResolutionKind.KNOWN);
        assertThat(monstera.canonicalPlantId()).isEqualTo("3");
        verify(disambiguator, never()).disambiguate(any(), any(), any());
    }

    @Test
    void shouldResolveConfiguredAliasWithoutLlm() {
        var resolution = resolver.resolve(RagQuery.of("虎皮兰需要什么光照？"));

        assertThat(resolution.kind()).isEqualTo(PlantEntityResolver.ResolutionKind.KNOWN);
        assertThat(resolution.canonicalPlantId()).isEqualTo("2");
        assertThat(resolution.method()).isEqualTo(PlantEntityResolver.ResolutionMethod.ALIAS);
    }

    @Test
    void shouldResolveConfiguredAliasAcrossWateringFrequencyPhrases() {
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
            assertThat(resolution.kind()).isEqualTo(PlantEntityResolver.ResolutionKind.KNOWN);
            assertThat(resolution.canonicalPlantId()).isEqualTo("1");
            assertThat(resolution.method()).isEqualTo(PlantEntityResolver.ResolutionMethod.ALIAS);
        });
    }

    @Test
    void shouldResolveAllExactEntitiesInComparison() {
        var resolution = resolver.resolve(RagQuery.of("红掌和白掌的光照要求一样吗？"));

        assertThat(resolution.kind()).isEqualTo(PlantEntityResolver.ResolutionKind.KNOWN);
        assertThat(resolution.canonicalPlantIds()).containsExactlyInAnyOrder("20", "21");
    }

    @Test
    void shouldResolveComparisonWhenRightEntityHasNaturalLanguageSuffix() {
        var resolution = resolver.resolve(RagQuery.of("绿萝和虎尾兰适宜温度分别是多少？"));

        assertThat(resolution.kind()).isEqualTo(PlantEntityResolver.ResolutionKind.KNOWN);
        assertThat(resolution.canonicalPlantIds()).containsExactlyInAnyOrder("1", "2");
    }

    @Test
    void shouldRejectComparisonWhenAnyMentionIsUnknown() {
        var resolution = resolver.resolve(RagQuery.of("绿萝和常春藤的浇水方法相同吗？"));

        assertThat(resolution.kind()).isEqualTo(PlantEntityResolver.ResolutionKind.UNKNOWN);
        assertThat(resolution.rejectionReason()).isEqualTo("comparison_entity_unresolved");
    }

    @Test
    void shouldResolveKnownNameWithNaturalLanguageContextWithoutLlm() {
        disambiguator = mock(PlantEntityDisambiguator.class);
        resolver = new PlantEntityResolver(mockRepository(), entityStore, null, new RagProperties(), null, disambiguator);

        assertThat(resolver.resolve(RagQuery.of("我的绿萝需要什么光照？")).method())
                .isEqualTo(PlantEntityResolver.ResolutionMethod.EXACT_NAME);
        assertThat(resolver.resolve(RagQuery.of("这盆绿萝需要什么光照？")).method())
                .isEqualTo(PlantEntityResolver.ResolutionMethod.EXACT_NAME);
        verify(disambiguator, never()).disambiguate(any(), any(), any());
    }

    @Test
    void shouldRejectKnownNameEmbeddedInUnknownCompoundWhenLlmDoesNotConfirm() {
        useLlmDecision(PlantEntityDisambiguator.Decision.unknown("llm_rejected_or_unknown"));
        var compound = resolver.resolve(RagQuery.of("月球绿萝需要浇水吗？"));
        assertThat(compound.kind()).isEqualTo(PlantEntityResolver.ResolutionKind.UNKNOWN);
        verify(disambiguator).disambiguate(any(), any(), any());
    }

    @Test
    void shouldRejectUnknownCompoundBuiltFromAliasWhenLlmDoesNotConfirm() {
        useLlmDecision(PlantEntityDisambiguator.Decision.unknown("llm_rejected_or_unknown"));

        var compound = resolver.resolve(RagQuery.of("量子虎皮兰的根腐怎么处理？"));

        assertThat(compound.kind()).isEqualTo(PlantEntityResolver.ResolutionKind.UNKNOWN);
        assertThat(compound.canonicalPlantId()).isEmpty();
        verify(disambiguator).disambiguate(any(), any(), any());
    }

    @Test
    void shouldAcceptKnownPlantWhenNaturalLanguageContextWrapsTheMention() {
        useLlmDecision(PlantEntityDisambiguator.Decision.known("1", 0.95));
        assertThat(List.of(
                "绿萝建议多久浇一次水？",
                "绿萝出现枯黄叶片时怎么处理？",
                "绿萝官方浇水频率是什么？",
                "绿萝耐阴吗？"
        )).allSatisfy(query -> {
            var resolution = resolver.resolve(RagQuery.of(query));
            assertThat(resolution.kind()).isEqualTo(PlantEntityResolver.ResolutionKind.KNOWN);
            assertThat(resolution.canonicalPlantId()).isEqualTo("1");
            assertThat(resolution.method()).isEqualTo(PlantEntityResolver.ResolutionMethod.EXACT_NAME);
        });
        assertThat(List.of(
                "社区里有没有绿萝的日常养护经验？",
                "大家分享的绿萝经验里，耐阴等于喜阴吗？",
                "社区用户遇到绿萝状态变化时是怎么判断的？",
                "社区经验里绿萝浇水要避免什么情况？"
        )).allSatisfy(query -> {
            var resolution = resolver.resolve(RagQuery.of(query));
            assertThat(resolution.kind()).isEqualTo(PlantEntityResolver.ResolutionKind.KNOWN);
            assertThat(resolution.canonicalPlantId()).isEqualTo("1");
            assertThat(resolution.method()).isEqualTo(PlantEntityResolver.ResolutionMethod.EXACT_NAME);
        });
        verify(disambiguator, never()).disambiguate(any(), any(), any());
    }

    @Test
    void shouldResolveKnownAliasesWithoutCallingLlm() {
        disambiguator = mock(PlantEntityDisambiguator.class);
        when(disambiguator.disambiguate(any(), any(), any())).thenReturn(
                PlantEntityDisambiguator.Decision.known("2", 0.95),
                PlantEntityDisambiguator.Decision.known("1", 0.95),
                PlantEntityDisambiguator.Decision.known("3", 0.95));
        resolver = new PlantEntityResolver(mockRepository(), entityStore, null, new RagProperties(), null, disambiguator);
        when(entityStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                entityHit("2", "虎尾兰", 0.84),
                entityHit("1", "绿萝", 0.82),
                entityHit("3", "龟背竹", 0.80)
        ));

        assertThat(resolver.resolve(RagQuery.of("虎皮兰需要什么光照？")).canonicalPlantId()).isEqualTo("2");
        assertThat(resolver.resolve(RagQuery.of("黄金葛一周浇几次水？")).canonicalPlantId()).isEqualTo("1");
        assertThat(resolver.resolve(RagQuery.of("蓬莱蕉适合什么光照？")).canonicalPlantId()).isEqualTo("3");
        verify(disambiguator, never()).disambiguate(any(), any(), any());
    }

    @Test
    void shouldResolveKnownPlantsBehindCommunityAndUserPrefixesWithoutLlm() {
        disambiguator = mock(PlantEntityDisambiguator.class);
        resolver = new PlantEntityResolver(mockRepository(), entityStore, null, new RagProperties(), null, disambiguator);

        assertThat(resolver.resolve(RagQuery.of("社区里白掌缺水时叶片会有什么表现？")).canonicalPlantId())
                .isEqualTo("21");
        assertThat(resolver.resolve(RagQuery.of("网友遇到虎尾兰叶基发软时先检查什么？")).canonicalPlantId())
                .isEqualTo("2");
        assertThat(resolver.resolve(RagQuery.of("论坛帖里白掌缺水时叶片会有什么表现？")).canonicalPlantId())
                .isEqualTo("21");
        assertThat(resolver.resolve(RagQuery.of("花友记录的虎尾兰叶基发软时先检查什么？")).canonicalPlantId())
                .isEqualTo("2");
        verify(disambiguator, never()).disambiguate(any(), any(), any());
    }

    @Test
    void shouldUseAliasCandidateFallbackWhenUnseenContextMissesRegex() {
        useLlmDecision(PlantEntityDisambiguator.Decision.known("1", 0.95));

        var resolution = resolver.resolve(RagQuery.of("听说黄金葛在北向窗边也能长，冬天还需要补水吗？"));

        assertThat(resolution.kind()).isEqualTo(PlantEntityResolver.ResolutionKind.KNOWN);
        assertThat(resolution.canonicalPlantId()).isEqualTo("1");
        assertThat(resolution.method()).isEqualTo(PlantEntityResolver.ResolutionMethod.LLM);
        verify(disambiguator).disambiguate(any(), org.mockito.ArgumentMatchers.eq("黄金葛"), any());
    }

    @Test
    void shouldNotCallLlmWhenCareQueryHasNoEntityMention() {
        disambiguator = mock(PlantEntityDisambiguator.class);
        resolver = new PlantEntityResolver(mockRepository(), entityStore, null, new RagProperties(), null, disambiguator);
        when(entityStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                entityHit("1", "绿萝", 0.81), entityHit("10", "芦荟", 0.79)
        ));

        var resolution = resolver.resolve(RagQuery.of("多久浇一次水？"));

        assertThat(resolution.kind()).isEqualTo(PlantEntityResolver.ResolutionKind.UNKNOWN);
        verify(disambiguator, never()).disambiguate(any(), any(), any());
    }

    @Test
    void shouldKeepGenericPlantCareConceptQuestionsOpen() {
        var resolution = resolver.resolve(RagQuery.of("耐阴等于喜阴吗？"));

        assertThat(resolution.kind()).isEqualTo(PlantEntityResolver.ResolutionKind.GENERIC);
    }

    @Test
    void shouldNotAcceptCloseVectorCandidatesWithoutLlmConfirmation() {
        when(entityStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                entityHit("1", "绿萝", 0.85), entityHit("10", "芦荟", 0.82)
        ));

        var resolution = resolver.resolve(RagQuery.of("某种室内植物适合什么光照？"));

        assertThat(resolution.kind()).isEqualTo(PlantEntityResolver.ResolutionKind.UNKNOWN);
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
        useLlmDecision(PlantEntityDisambiguator.Decision.known("1", 0.95));
        var resolution = resolver.resolve(RagQuery.of("社区里的绿萝养护经验"));
        KnowledgeDocument community = document("", "", "作者记录了绿萝的日常浇水习惯");

        assertThat(resolver.matches(resolution, community)).isTrue();
    }

    private KnowledgeRepository.PlantEntityRow entityPlant(String id, String scientificName, String commonName) {
        List<String> aliases = switch (commonName) {
            case "绿萝" -> List.of("黄金葛", "绿箩");
            case "虎尾兰" -> List.of("虎皮兰", "虎尾蓝");
            case "龟背竹" -> List.of("蓬莱蕉", "龟背主");
            default -> List.of();
        };
        return new KnowledgeRepository.PlantEntityRow(id, scientificName, commonName, aliases);
    }

    private void useLlmDecision(PlantEntityDisambiguator.Decision decision) {
        disambiguator = mock(PlantEntityDisambiguator.class);
        when(disambiguator.disambiguate(any(), any(), any())).thenReturn(decision);
        resolver = new PlantEntityResolver(mockRepository(), entityStore, null, new RagProperties(), null, disambiguator);
    }

    private KnowledgeRepository mockRepository() {
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        when(repository.findPlantEntities()).thenReturn(List.of(
                entityPlant("1", "Epipremnum aureum", "绿萝"),
                entityPlant("2", "Sansevieria trifasciata", "虎尾兰"),
                entityPlant("3", "Monstera deliciosa", "龟背竹"),
                entityPlant("10", "Aloe vera", "芦荟"),
                entityPlant("20", "Anthurium andraeanum", "红掌"),
                entityPlant("21", "Spathiphyllum wallisii", "白掌")
        ));
        return repository;
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
