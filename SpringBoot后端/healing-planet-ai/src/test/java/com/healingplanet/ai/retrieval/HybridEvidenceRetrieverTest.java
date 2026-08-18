package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.domain.RagQuery;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
}
