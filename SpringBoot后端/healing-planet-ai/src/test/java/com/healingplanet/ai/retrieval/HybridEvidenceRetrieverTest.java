package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.domain.KnowledgeSource;
import com.healingplanet.ai.domain.RagQuery;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HybridEvidenceRetrieverTest {
    private VectorStore plantStore;
    private SparseIndexService sparseIndex;
    private PlantEntityResolver entityResolver;
    private HybridEvidenceRetriever retriever;
    private RagProperties properties;

    @BeforeEach
    void setUp() {
        plantStore = mock(VectorStore.class);
        VectorStore communityStore = mock(VectorStore.class);
        sparseIndex = mock(SparseIndexService.class);
        entityResolver = mock(PlantEntityResolver.class);
        properties = new RagProperties();
        properties.getReranker().setEnabled(false);
        properties.getSourceAwareRanking().setEnabled(false);
        properties.getEvidenceSelector().setEnabled(false);
        retriever = new HybridEvidenceRetriever(plantStore, communityStore, sparseIndex,
                new KnowledgeDocumentMapper(), mock(Reranker.class), new SourceAwareRanker(properties),
                entityResolver, new EvidenceSelector(properties), new RetrievalMetrics(new SimpleMeterRegistry()), properties);
    }

    @Test
    void shouldPushSinglePlantScopeIntoDenseAndSparseSearchBeforeTopK() {
        var entity = known("1");
        when(entityResolver.resolve(any())).thenReturn(entity);
        when(plantStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(document("p1", "1")));
        when(sparseIndex.search(any(), any(), any(Integer.class), any())).thenReturn(List.of());

        assertThat(retriever.retrieve(RagQuery.of("绿萝光照"))).isNotEmpty();

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

        retriever.retrieve(RagQuery.of("绿萝和虎尾兰光照"));

        ArgumentCaptor<SearchRequest> dense = ArgumentCaptor.forClass(SearchRequest.class);
        verify(plantStore).similaritySearch(dense.capture());
        assertThat(dense.getValue().getFilterExpression().toString()).contains("canonicalPlantId", "1", "2");
        verify(sparseIndex).search(KnowledgeSource.PLANT, "绿萝和虎尾兰光照", properties.getSparseTopK(), List.of("1", "2"));
    }

    @Test
    void shouldNotTurnUnknownEntityIntoRetrievalHardGate() {
        var unknown = new PlantEntityResolver.Resolution(PlantEntityResolver.ResolutionKind.UNKNOWN, "", Set.of());
        when(entityResolver.resolve(any())).thenReturn(unknown);
        when(plantStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(document("generic", "2")));
        when(sparseIndex.search(any(), any(), anyInt())).thenReturn(List.of());

        assertThat(retriever.retrieve(RagQuery.of("火星苔藓适合什么光照？"))).isNotEmpty();
        ArgumentCaptor<SearchRequest> dense = ArgumentCaptor.forClass(SearchRequest.class);
        verify(plantStore).similaritySearch(dense.capture());
        assertThat(dense.getValue().hasFilterExpression()).isFalse();
    }

    private PlantEntityResolver.Resolution known(String id) {
        return new PlantEntityResolver.Resolution(PlantEntityResolver.ResolutionKind.KNOWN, id, Set.of("绿萝"));
    }

    private org.springframework.ai.document.Document document(String id, String plantId) {
        return org.springframework.ai.document.Document.builder().id(id).text(id)
                .metadata("canonicalPlantId", plantId).metadata("chunkId", id).metadata("sourceId", id)
                .metadata("knowledgeType", "LIGHT").metadata("title", id).metadata("plantName", "绿萝")
                .metadata("trustScore", 1d).metadata("createdAt", Instant.now().toString()).score(0.9).build();
    }
}
