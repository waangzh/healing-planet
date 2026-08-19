package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.domain.KnowledgeSource;
import com.healingplanet.ai.domain.RagQuery;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

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

    @BeforeEach
    void setUp() {
        plantStore = mock(VectorStore.class);
        communityStore = mock(VectorStore.class);
        sparseIndex = mock(SparseIndexService.class);
        entityResolver = mock(PlantEntityResolver.class);
        reranker = mock(Reranker.class);
        when(plantStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(communityStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(sparseIndex.search(any(), any(), anyInt())).thenReturn(List.of());
        when(reranker.rerank(any(), any())).thenReturn(Map.of());

        retriever = new HybridEvidenceRetriever(plantStore, communityStore, sparseIndex,
                new KnowledgeDocumentMapper(), reranker, new SourceAwareRanker(), entityResolver,
                new RetrievalMetrics(new SimpleMeterRegistry()), new RagProperties());
    }

    @Test
    void shouldApplyCanonicalPlantFilterBeforeDenseSearch() {
        var entity = new PlantEntityResolver.Resolution(
                PlantEntityResolver.ResolutionKind.KNOWN, "1", Set.of("绿萝"));
        RagQuery query = new RagQuery("绿萝适合什么光照？", null, null, null,
                null, List.of(), Map.of("includeCommunity", false));
        when(entityResolver.resolve(query)).thenReturn(entity);

        retriever.retrieve(query);

        ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
        verify(plantStore).similaritySearch(request.capture());
        assertThat(request.getValue().hasFilterExpression()).isTrue();
        assertThat(request.getValue().getFilterExpression().toString()).contains("canonicalPlantId", "1");
    }

    @Test
    void shouldSearchEachResolvedPlantWithItsOwnCanonicalFilter() {
        var entity = new PlantEntityResolver.Resolution(
                PlantEntityResolver.ResolutionKind.KNOWN, "20", List.of("20", "21"),
                Set.of("红掌", "白掌"), PlantEntityResolver.ResolutionMethod.EXACT_NAME,
                1, 0, 1, 2, "");
        RagQuery query = new RagQuery("红掌和白掌的光照要求一样吗？", null, null, null,
                null, List.of(), Map.of("includeCommunity", false));
        when(entityResolver.resolve(query)).thenReturn(entity);

        var result = retriever.retrieveWithDiagnostics(query);

        ArgumentCaptor<SearchRequest> requests = ArgumentCaptor.forClass(SearchRequest.class);
        verify(plantStore, times(2)).similaritySearch(requests.capture());
        assertThat(requests.getAllValues()).allMatch(SearchRequest::hasFilterExpression);
        assertThat(requests.getAllValues()).extracting(request -> request.getFilterExpression().toString())
                .anyMatch(filter -> filter.contains("20"))
                .anyMatch(filter -> filter.contains("21"));
        assertThat(result.entityResolution().canonicalPlantIds()).containsExactly("20", "21");
    }

    @Test
    void shouldStopBeforeSearchingWhenNamedPlantIsUnknown() {
        RagQuery query = RagQuery.of("火星苔藓适合什么光照？");
        when(entityResolver.resolve(query)).thenReturn(new PlantEntityResolver.Resolution(
                PlantEntityResolver.ResolutionKind.UNKNOWN, "", Set.of()));

        assertThat(retriever.retrieve(query)).isEmpty();

        verify(plantStore, never()).similaritySearch(any(SearchRequest.class));
        verify(communityStore, never()).similaritySearch(any(SearchRequest.class));
        verify(sparseIndex, never()).search(any(), any(), anyInt());
    }

    @Test
    void shouldStopBeforeSearchingWhenQueryIsOutsidePlantCareDomain() {
        RagQuery query = RagQuery.of("量子纠缠是什么？");
        when(entityResolver.resolve(query)).thenReturn(new PlantEntityResolver.Resolution(
                PlantEntityResolver.ResolutionKind.OUT_OF_DOMAIN, "", Set.of()));

        assertThat(retriever.retrieve(query)).isEmpty();

        verify(plantStore, never()).similaritySearch(any(SearchRequest.class));
        verify(communityStore, never()).similaritySearch(any(SearchRequest.class));
        verify(sparseIndex, never()).search(any(), any(), anyInt());
    }

    @Test
    void shouldStopBeforeSearchingWhenPlantEntityIsAmbiguous() {
        RagQuery query = RagQuery.of("某种室内植物适合什么光照？");
        when(entityResolver.resolve(query)).thenReturn(new PlantEntityResolver.Resolution(
                PlantEntityResolver.ResolutionKind.AMBIGUOUS, "", Set.of()));

        assertThat(retriever.retrieve(query)).isEmpty();

        verify(plantStore, never()).similaritySearch(any(SearchRequest.class));
        verify(communityStore, never()).similaritySearch(any(SearchRequest.class));
        verify(sparseIndex, never()).search(any(), any(), anyInt());
    }

    @Test
    void shouldKeepLowScoringCommunityEvidenceWhenFormalKnowledgeTypeIsRequired() {
        RagProperties properties = new RagProperties();
        properties.getEval().setRetrievalTraceEnabled(true);
        retriever = new HybridEvidenceRetriever(plantStore, communityStore, sparseIndex,
                new KnowledgeDocumentMapper(), reranker, new SourceAwareRanker(), entityResolver,
                new RetrievalMetrics(new SimpleMeterRegistry()), properties);
        var entity = new PlantEntityResolver.Resolution(
                PlantEntityResolver.ResolutionKind.KNOWN, "1", Set.of("绿萝"));
        RagQuery query = new RagQuery("绿萝光照和社区经验", null, null, null,
                null, List.of(), Map.of("requiredKnowledgeType", "LIGHT"));
        when(entityResolver.resolve(query)).thenReturn(entity);
        when(entityResolver.matches(any(), any())).thenReturn(true);
        when(plantStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                document("p1", "LIGHT"), document("p2", "LIGHT"), document("p3", "LIGHT"),
                document("p4", "LIGHT"), document("p5", "LIGHT"), document("p6", "LIGHT")));
        when(communityStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(communityDocument("c1")));
        when(reranker.rerank(any(), any())).thenReturn(Map.of("c1", 0.1));

        var result = retriever.retrieveWithDiagnostics(query);

        assertThat(result.evidence()).extracting(com.healingplanet.ai.domain.Evidence::type)
                .contains(com.healingplanet.ai.domain.EvidenceType.CARE_GUIDE,
                        com.healingplanet.ai.domain.EvidenceType.COMMUNITY_POST);
        assertThat(result.retrievalTrace().selected()).extracting(item -> item.reason())
                .contains("SOURCE_RETENTION");
    }

    @Test
    void shouldMarkEvidenceSelectedByEntityQuota() {
        RagProperties properties = new RagProperties();
        properties.getEval().setRetrievalTraceEnabled(true);
        retriever = new HybridEvidenceRetriever(plantStore, communityStore, sparseIndex,
                new KnowledgeDocumentMapper(), reranker, new SourceAwareRanker(), entityResolver,
                new RetrievalMetrics(new SimpleMeterRegistry()), properties);
        var entity = new PlantEntityResolver.Resolution(
                PlantEntityResolver.ResolutionKind.KNOWN, "20", List.of("20", "21"),
                Set.of("红掌", "白掌"), PlantEntityResolver.ResolutionMethod.EXACT_NAME,
                1, 0, 1, 2, "");
        RagQuery query = new RagQuery("红掌和白掌的光照要求一样吗？", null, null, null,
                null, List.of(), Map.of("includeCommunity", false));
        when(entityResolver.resolve(query)).thenReturn(entity);
        when(entityResolver.matches(any(), any())).thenReturn(true);
        when(plantStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(documentForPlant("plant-20", "20", "红掌", "LIGHT")))
                .thenReturn(List.of(documentForPlant("plant-21", "21", "白掌", "LIGHT")));

        var result = retriever.retrieveWithDiagnostics(query);

        assertThat(result.retrievalTrace().selected()).hasSize(2)
                .allMatch(item -> item.reason().equals("ENTITY_QUOTA"));
    }

    @Test
    void shouldExposeRetrievalSnapshotsWhenEvalTraceIsEnabled() {
        RagProperties properties = new RagProperties();
        properties.getEval().setRetrievalTraceEnabled(true);
        retriever = new HybridEvidenceRetriever(plantStore, communityStore, sparseIndex,
                new KnowledgeDocumentMapper(), reranker, new SourceAwareRanker(), entityResolver,
                new RetrievalMetrics(new SimpleMeterRegistry()), properties);
        var entity = new PlantEntityResolver.Resolution(
                PlantEntityResolver.ResolutionKind.KNOWN, "1", Set.of("绿萝"));
        RagQuery query = new RagQuery("绿萝需要什么光照？", null, null, null,
                null, List.of(), Map.of("includeCommunity", false, "requiredKnowledgeType", "LIGHT"));
        var denseDocument = document("dense-1", "LIGHT");
        var sparseDocument = new KnowledgeDocumentMapper().fromSpring(document("sparse-1", "LIGHT"),
                KnowledgeSource.PLANT);
        when(entityResolver.resolve(query)).thenReturn(entity);
        when(entityResolver.matches(any(), any())).thenReturn(true);
        when(plantStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(denseDocument));
        when(sparseIndex.search(KnowledgeSource.PLANT, query.query(), properties.getSparseTopK()))
                .thenReturn(List.of(new SparseIndexService.SparseHit(sparseDocument, 0.8)));
        when(reranker.rerank(any(), any())).thenReturn(Map.of("dense-1", 0.7, "sparse-1", 0.9));

        var trace = retriever.retrieveWithDiagnostics(query).retrievalTrace();

        assertThat(trace).isNotNull();
        assertThat(trace.entityResolution().resolutionKind()).isEqualTo("KNOWN");
        assertThat(trace.denseTopK()).extracting(item -> item.id()).containsExactly("dense-1");
        assertThat(trace.sparseTopK()).extracting(item -> item.id()).containsExactly("sparse-1");
        assertThat(trace.rrfCandidates()).hasSize(2);
        assertThat(trace.knowledgeTypeFiltered()).hasSize(2);
        assertThat(trace.rerankBefore()).extracting(item -> item.id())
                .containsExactly("dense-1", "sparse-1");
        assertThat(trace.rerankAfter()).extracting(item -> item.id())
                .containsExactly("sparse-1", "dense-1");
        assertThat(trace.selected()).allMatch(item -> item.reason().equals("GLOBAL_RANKING"));
        assertThat(trace.stages()).extracting(item -> item.stage()).contains(
                "entity_resolve", "dense_search", "sparse_search", "rrf_fusion",
                "knowledge_type_filter", "rerank", "final_rank", "knowledge_total");
    }

    private org.springframework.ai.document.Document document(String id, String knowledgeType) {
        return documentForPlant(id, "1", "绿萝", knowledgeType);
    }

    private org.springframework.ai.document.Document documentForPlant(String id, String canonicalPlantId,
                                                                       String plantName, String knowledgeType) {
        return org.springframework.ai.document.Document.builder().id(id).text(id)
                .metadata("canonicalPlantId", canonicalPlantId)
                .metadata("chunkId", id)
                .metadata("knowledgeType", knowledgeType)
                .metadata("sourceId", id)
                .metadata("title", id)
                .metadata("plantName", plantName)
                .metadata("trustScore", 1d)
                .metadata("createdAt", Instant.now().toString())
                .score(0.95)
                .build();
    }

    private org.springframework.ai.document.Document communityDocument(String id) {
        return org.springframework.ai.document.Document.builder().id(id).text(id)
                .metadata("chunkId", id)
                .metadata("knowledgeType", "COMMUNITY_EXPERIENCE")
                .metadata("sourceId", id)
                .metadata("title", id)
                .metadata("plantName", "绿萝")
                .metadata("trustScore", 1d)
                .metadata("essence", true)
                .metadata("likes", 30)
                .metadata("collects", 8)
                .metadata("comments", 5)
                .metadata("views", 200)
                .metadata("createdAt", Instant.now().toString())
                .score(0.92)
                .build();
    }
}
