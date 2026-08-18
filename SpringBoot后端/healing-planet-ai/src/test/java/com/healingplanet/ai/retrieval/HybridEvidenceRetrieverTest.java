package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.config.RagProperties;
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
    private HybridEvidenceRetriever retriever;

    @BeforeEach
    void setUp() {
        plantStore = mock(VectorStore.class);
        communityStore = mock(VectorStore.class);
        sparseIndex = mock(SparseIndexService.class);
        entityResolver = mock(PlantEntityResolver.class);
        Reranker reranker = mock(Reranker.class);
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
    void shouldKeepCommunityCandidatesWhenFormalKnowledgeTypeIsRequired() {
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

        var result = retriever.retrieve(query);

        assertThat(result).extracting(com.healingplanet.ai.domain.Evidence::type)
                .contains(com.healingplanet.ai.domain.EvidenceType.CARE_GUIDE,
                        com.healingplanet.ai.domain.EvidenceType.COMMUNITY_POST);
    }

    private org.springframework.ai.document.Document document(String id, String knowledgeType) {
        return org.springframework.ai.document.Document.builder().id(id).text(id)
                .metadata("canonicalPlantId", "1")
                .metadata("chunkId", id)
                .metadata("knowledgeType", knowledgeType)
                .metadata("sourceId", id)
                .metadata("title", id)
                .metadata("plantName", "绿萝")
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
