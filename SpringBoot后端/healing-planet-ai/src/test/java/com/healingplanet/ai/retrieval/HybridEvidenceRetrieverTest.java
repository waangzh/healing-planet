package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.config.RagRuntimeConfig;
import com.healingplanet.ai.domain.KnowledgeSource;
import com.healingplanet.ai.domain.RagQuery;
import com.healingplanet.ai.domain.QueryIntent;
import com.healingplanet.ai.query.QueryAnalysis;
import com.healingplanet.ai.query.RetrievalConstraints;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HybridEvidenceRetrieverTest {
    private VectorStore plantStore;
    private VectorStore communityStore;
    private SparseIndexService sparseIndex;
    private PlantEntityResolver entityResolver;
    private Reranker reranker;
    private HybridEvidenceRetriever retriever;
    private RagProperties properties;

    @BeforeEach
    void setUp() {
        plantStore = mock(VectorStore.class);
        communityStore = mock(VectorStore.class);
        sparseIndex = mock(SparseIndexService.class);
        entityResolver = mock(PlantEntityResolver.class);
        reranker = mock(Reranker.class);
        properties = new RagProperties();
        properties.getReranker().setEnabled(false);
        properties.getSourceAwareRanking().setEnabled(false);
        properties.getEvidenceSelector().setEnabled(false);
        properties.getAdaptiveRecall().setEnabled(false);
        retriever = new HybridEvidenceRetriever(plantStore, communityStore, sparseIndex,
                new KnowledgeDocumentMapper(), reranker, new SourceAwareRanker(properties),
                entityResolver, new EvidenceSelector(properties), new RetrievalMetrics(new SimpleMeterRegistry()), properties);
    }

    @Test
    void shouldPushSinglePlantScopeIntoDenseAndSparseSearchBeforeTopK() {
        var entity = known("1");
        when(entityResolver.resolve(any())).thenReturn(entity);
        when(plantStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(document("p1", "1")));
        when(sparseIndex.search(any(), any(), any(Integer.class), any())).thenReturn(List.of());

        assertThat(retriever.retrieve(request("绿萝光照"))).isNotEmpty();

        ArgumentCaptor<SearchRequest> dense = ArgumentCaptor.forClass(SearchRequest.class);
        verify(plantStore).similaritySearch(dense.capture());
        assertThat(dense.getValue().getFilterExpression().toString()).contains("canonicalPlantId", "1");
        verify(sparseIndex).search(KnowledgeSource.PLANT, "绿萝光照", properties.getSparseTopK(), List.of("1"));
    }

    @Test
    void shouldUseOneMultiValuePreFilterForMultiplePlants() {
        var entity = new PlantEntityResolver.Resolution(PlantEntityResolver.ResolutionKind.KNOWN, "1",
                List.of("1", "2"), Set.of("绿萝", "虎尾兰"), PlantEntityResolver.ResolutionMethod.EXACT_NAME,
                1, 0, 1, 2, "");
        when(entityResolver.resolve(any())).thenReturn(entity);
        when(plantStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(document("p1", "1"), document("p2", "2")));
        when(sparseIndex.search(any(), any(), any(Integer.class), any())).thenReturn(List.of());

        retriever.retrieve(request("绿萝和虎尾兰光照"));

        ArgumentCaptor<SearchRequest> dense = ArgumentCaptor.forClass(SearchRequest.class);
        verify(plantStore).similaritySearch(dense.capture());
        assertThat(dense.getValue().getFilterExpression().toString()).contains("canonicalPlantId", "1", "2");
        verify(sparseIndex).search(KnowledgeSource.PLANT, "绿萝和虎尾兰光照", properties.getSparseTopK(), List.of("1", "2"));
    }

    @Test
    void shouldBlockSpeciesKnowledgeForSpecificUnknownEntity() {
        var unknown = new PlantEntityResolver.Resolution(PlantEntityResolver.ResolutionKind.UNKNOWN, "", Set.of());
        when(entityResolver.resolve(any())).thenReturn(unknown);
        when(plantStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(document("generic", "2")));

        assertThat(retriever.retrieve(request("火星苔藓适合什么光照？"))).isEmpty();
        verify(plantStore, never()).similaritySearch(any(SearchRequest.class));
        verify(communityStore, never()).similaritySearch(any(SearchRequest.class));
        verify(sparseIndex, never()).search(any(), any(), anyInt());
    }

    @Test
    void shouldBlockSpeciesKnowledgeForAmbiguousAndConflictingEntity() {
        for (PlantEntityResolver.ResolutionKind kind : List.of(
                PlantEntityResolver.ResolutionKind.AMBIGUOUS,
                PlantEntityResolver.ResolutionKind.CONFLICT)) {
            org.mockito.Mockito.reset(plantStore, communityStore, sparseIndex, entityResolver);
            when(entityResolver.resolve(any())).thenReturn(new PlantEntityResolver.Resolution(kind, "", Set.of()));

            assertThat(retriever.retrieve(request("万年青需要什么光照？"))).as(kind.name()).isEmpty();
            verify(plantStore, never()).similaritySearch(any(SearchRequest.class));
            verify(communityStore, never()).similaritySearch(any(SearchRequest.class));
        }
    }

    @Test
    void shouldAllowGenericUnscopedPlantQuery() {
        var generic = new PlantEntityResolver.Resolution(PlantEntityResolver.ResolutionKind.GENERIC, "", Set.of());
        when(entityResolver.resolve(any())).thenReturn(generic);
        when(plantStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(document("generic", "2")));
        when(sparseIndex.search(any(), any(), anyInt())).thenReturn(List.of());

        assertThat(retriever.retrieve(request("什么植物比较耐阴？"))).isNotEmpty();
        ArgumentCaptor<SearchRequest> dense = ArgumentCaptor.forClass(SearchRequest.class);
        verify(plantStore).similaritySearch(dense.capture());
        assertThat(dense.getValue().hasFilterExpression()).isFalse();
    }

    @Test
    void shouldFilterSoftFuzzyScopeInsteadOfSearchingAllPlants() {
        var soft = new PlantEntityResolver.Resolution(PlantEntityResolver.ResolutionKind.KNOWN, "2", List.of("2"),
                Set.of("虎尾兰"), PlantEntityResolver.ResolutionMethod.FUZZY, 0.67, 0.2, 0.47, 2, "",
                List.of(), List.of(), List.of(), PlantScope.soft(List.of("2")));
        when(entityResolver.resolve(any())).thenReturn(soft);
        when(plantStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(document("p2", "2")));
        when(sparseIndex.search(any(), any(), any(Integer.class), any())).thenReturn(List.of());

        retriever.retrieve(request("虎尾蓝多久浇水？"));

        ArgumentCaptor<SearchRequest> dense = ArgumentCaptor.forClass(SearchRequest.class);
        verify(plantStore).similaritySearch(dense.capture());
        assertThat(dense.getValue().getFilterExpression().toString()).contains("canonicalPlantId", "2");
        verify(sparseIndex).search(KnowledgeSource.PLANT, "虎尾蓝多久浇水？", properties.getSparseTopK(), List.of("2"));
    }

    @Test
    void shouldRetrieveOnlyKnownPlantForPartialAndSkipUnscopedCommunity() {
        var partial = new PlantEntityResolver.Resolution(PlantEntityResolver.ResolutionKind.PARTIAL, "1", List.of("1"),
                Set.of("绿萝"), PlantEntityResolver.ResolutionMethod.EXACT_NAME, 1, 0, 1, 1,
                "partial_entity_unresolved", List.of(), List.of("常春藤"));
        when(entityResolver.resolve(any())).thenReturn(partial);
        when(plantStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(document("p1", "1")));
        when(sparseIndex.search(any(), any(), any(Integer.class), any())).thenReturn(List.of());

        assertThat(retriever.retrieve(request("绿萝和常春藤的光照要求一样吗？"))).isNotEmpty();
        verify(communityStore, never()).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void topicHintCoverageMustNotCreateAFakeRerankScore() {
        when(entityResolver.resolve(any())).thenReturn(known("1"));
        org.springframework.ai.document.Document temperatureGuide = org.springframework.ai.document.Document.builder()
                .id("temperature").text("temperature")
                .metadata("canonicalPlantId", "1").metadata("chunkId", "temperature")
                .metadata("sourceId", "temperature").metadata("knowledgeType", "TEMPERATURE")
                .metadata("title", "temperature").metadata("plantName", "绿萝")
                .metadata("trustScore", 1d).metadata("createdAt", Instant.now().toString()).score(0.9).build();
        when(plantStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(temperatureGuide));
        when(sparseIndex.search(any(), any(), any(Integer.class), any())).thenReturn(List.of());

        var evidence = retriever.retrieve(request("绿萝光照"));

        assertThat(evidence).hasSize(1);
        assertThat(evidence.get(0).rerankScore()).isNull();
        assertThat(evidence.get(0).finalScore()).isGreaterThan(0.05d);
    }

    @Test
    void shouldDeepenOnlyTheMissingRequiredSource() {
        properties.getAdaptiveRecall().setEnabled(true);
        properties.getAdaptiveRecall().setMaxDenseTopK(60);
        properties.getAdaptiveRecall().setMinUniqueLogicalCandidates(1);
        properties.setRetrievalMode(RagProperties.RetrievalMode.DENSE_ONLY);
        when(entityResolver.resolve(any())).thenReturn(new PlantEntityResolver.Resolution(
                PlantEntityResolver.ResolutionKind.GENERIC, "", Set.of()));
        when(plantStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(document("p1", "1")));
        when(communityStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        SourcePlan sourcePlan = new SourcePlan(SourcePlan.SourceRequirement.ALLOWED,
                SourcePlan.SourceRequirement.REQUIRED, SourcePlan.SourceRequirement.FORBIDDEN);

        retriever.retrieveWithDiagnostics(request("绿萝浇水经验", sourcePlan), RagRuntimeConfig.from(properties));

        verify(plantStore, times(1)).similaritySearch(any(SearchRequest.class));
        verify(communityStore, times(2)).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void shouldAddEntityNameToCommunityGroupQueryWithoutFilteringTheCommunityStore() {
        properties.setRetrievalMode(RagProperties.RetrievalMode.DENSE_ONLY);
        PlantCatalogIndex catalog = mock(PlantCatalogIndex.class);
        when(catalog.canonicalPlantName("1")).thenReturn("绿萝");
        HybridEvidenceRetriever catalogAware = new HybridEvidenceRetriever(plantStore, communityStore, sparseIndex,
                new KnowledgeDocumentMapper(), reranker, new SourceAwareRanker(properties), entityResolver,
                new EvidenceSelector(properties), new RetrievalMetrics(new SimpleMeterRegistry()), properties, catalog);
        when(entityResolver.resolve(any())).thenReturn(known("1"));
        when(communityStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(document("post", "1")));
        SourcePlan sourcePlan = new SourcePlan(SourcePlan.SourceRequirement.FORBIDDEN,
                SourcePlan.SourceRequirement.ALLOWED, SourcePlan.SourceRequirement.FORBIDDEN);
        RetrievalQueryGroup group = new RetrievalQueryGroup("Q1", "对比绿萝和虎尾兰的社区经验", GroupRole.ENTITY_FOCUS,
                Set.of(), Set.of("1"), SourceScope.from(sourcePlan), true);
        RetrievalRequest request = new RetrievalRequest(RagQuery.of("对比绿萝和虎尾兰的社区经验"),
                new QueryAnalysis(QueryIntent.COMMUNITY_SEARCH, Set.of(), Set.of(), false, 0.9d),
                RetrievalConstraints.defaults(), new RetrievalPlan(sourcePlan, false, true, false, Set.of(), Set.of(),
                "对比绿萝和虎尾兰的社区经验", List.of(group)), null, "对比绿萝和虎尾兰的社区经验");

        catalogAware.retrieve(request);

        ArgumentCaptor<SearchRequest> dense = ArgumentCaptor.forClass(SearchRequest.class);
        verify(communityStore).similaritySearch(dense.capture());
        assertThat(dense.getValue().getQuery()).contains("对比绿萝和虎尾兰的社区经验", "目标植物：绿萝");
        assertThat(dense.getValue().hasFilterExpression()).isFalse();
    }

    @Test
    void shouldCorrectivelyDeepenRequiredSourceWhenInitialRerankCoverageIsWeak() {
        properties.getAdaptiveRecall().setEnabled(true);
        properties.getAdaptiveRecall().setMaxDenseTopK(60);
        properties.getAdaptiveRecall().setMinUniqueLogicalCandidates(1);
        properties.setRetrievalMode(RagProperties.RetrievalMode.DENSE_ONLY);
        properties.getReranker().setEnabled(true);
        when(entityResolver.resolve(any())).thenReturn(known("1"));
        when(plantStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(communityStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(document("post", "1")));
        when(reranker.rerank(any(), any())).thenReturn(Map.of("post", 0.1), Map.of("post", 0.3));
        SourcePlan sourcePlan = new SourcePlan(SourcePlan.SourceRequirement.ALLOWED,
                SourcePlan.SourceRequirement.REQUIRED, SourcePlan.SourceRequirement.FORBIDDEN);
        RetrievalRequest request = new RetrievalRequest(RagQuery.of("绿萝社区经验"),
                new QueryAnalysis(QueryIntent.COMMUNITY_SEARCH, Set.of(), Set.of(), false, 0.9d),
                RetrievalConstraints.defaults(), new RetrievalPlan(sourcePlan, true, true, false, Set.of(),
                Set.of(), "绿萝社区经验"), null, "绿萝社区经验");

        retriever.retrieveWithDiagnostics(request, RagRuntimeConfig.from(properties));

        verify(plantStore, times(1)).similaritySearch(any(SearchRequest.class));
        verify(communityStore, times(2)).similaritySearch(any(SearchRequest.class));
        verify(reranker, times(2)).rerank(any(), any());
    }

    private PlantEntityResolver.Resolution known(String id) {
        return new PlantEntityResolver.Resolution(PlantEntityResolver.ResolutionKind.KNOWN, id, Set.of("绿萝"));
    }

    private RetrievalRequest request(String text) {
        return request(text, new SourcePlan(SourcePlan.SourceRequirement.ALLOWED,
                SourcePlan.SourceRequirement.ALLOWED, SourcePlan.SourceRequirement.ALLOWED));
    }

    private RetrievalRequest request(String text, SourcePlan sourcePlan) {
        RagQuery query = RagQuery.of(text);
        return new RetrievalRequest(query, new QueryAnalysis(QueryIntent.GENERAL_CARE, Set.of(),
                KnowledgeTopicClassifier.classify(text), false, 0.9d), RetrievalConstraints.defaults(),
                new RetrievalPlan(sourcePlan, true, true, false, Set.of(),
                        KnowledgeTopicClassifier.classify(text), text), null, text);
    }

    private org.springframework.ai.document.Document document(String id, String plantId) {
        return org.springframework.ai.document.Document.builder().id(id).text(id)
                .metadata("canonicalPlantId", plantId).metadata("chunkId", id).metadata("sourceId", id)
                .metadata("knowledgeType", "LIGHT").metadata("title", id).metadata("plantName", "绿萝")
                .metadata("trustScore", 1d).metadata("createdAt", Instant.now().toString()).score(0.9).build();
    }
}
