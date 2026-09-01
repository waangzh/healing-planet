package com.healingplanet.ai.ingestion;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.domain.KnowledgeDocument;
import com.healingplanet.ai.domain.KnowledgeSource;
import com.healingplanet.ai.retrieval.PlantCatalogIndex;
import com.healingplanet.ai.retrieval.SparseIndexService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngestionServiceTest {

    @Test
    void plantIndexShouldRebuildEntityCollectionAndRefreshCatalog() {
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        when(repository.findPlantEntitiesAfter(anyString(), anyInt())).thenReturn(List.of(
                new KnowledgeRepository.PlantEntityRow("1", "Epipremnum aureum", "绿萝")));
        when(repository.findPlantsAfter(anyString(), anyInt())).thenReturn(List.of(
                new KnowledgeRepository.PlantRow("1", "Epipremnum aureum", "绿萝",
                        null, null, null, null, null, null)));

        SparseIndexService sparseIndex = mock(SparseIndexService.class);
        when(sparseIndex.ids(any())).thenReturn(Set.of());
        when(sparseIndex.documentsByIds(any(), any())).thenReturn(Map.of());
        EmbeddingStateRepository stateRepository = mock(EmbeddingStateRepository.class);
        when(stateRepository.documentIdsBySource(any())).thenReturn(Set.of());
        when(stateRepository.findByDocumentIds(any())).thenReturn(Map.of());
        VectorStore plantStore = mock(VectorStore.class);
        VectorStore entityStore = mock(VectorStore.class);
        VectorStore communityStore = mock(VectorStore.class);
        VectorStore diseaseStore = mock(VectorStore.class);
        PlantCatalogIndex catalogIndex = mock(PlantCatalogIndex.class);

        IngestionService service = new IngestionService(repository, new KnowledgeDocumentConverter(),
                new PlantEntityDocumentConverter(), catalogIndex, sparseIndex, plantStore, entityStore,
                communityStore, diseaseStore, mock(DiseaseKnowledgeRepository.class),
                mock(DiseaseKnowledgeConverter.class), stateRepository, new RagProperties());

        service.indexPlants();

        verify(entityStore).add(anyList());
        verify(sparseIndex).upsertAll(anyList());
        verify(catalogIndex).refresh();
    }

    @Test
    void unchangedPostShouldSkipEmbedding() {
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        KnowledgeRepository.PostRow row = new KnowledgeRepository.PostRow("post-1", "绿萝黄叶记录", "改善通风后恢复。",
                2, 1, 0, 10, false, java.time.Instant.parse("2026-01-01T00:00:00Z"), "绿萝");
        when(repository.findPublishedPost("post-1")).thenReturn(row);

        KnowledgeDocument document = new KnowledgeDocumentConverter().fromPost(row).get(0);
        EmbeddingStateRepository stateRepository = mock(EmbeddingStateRepository.class);
        when(stateRepository.documentIdsBySourceId(KnowledgeSource.COMMUNITY, "post-1")).thenReturn(Set.of());
        when(stateRepository.findByDocumentIds(any())).thenReturn(Map.of(document.id(),
                new EmbeddingStateRepository.EmbeddingState(document.id(), KnowledgeSource.COMMUNITY, "post-1",
                        String.valueOf(document.metadata().get("contentHash")), "BAAI/bge-m3")));

        SparseIndexService sparseIndex = mock(SparseIndexService.class);
        when(sparseIndex.idsBySourceId(KnowledgeSource.COMMUNITY, "post-1")).thenReturn(Set.of());
        when(sparseIndex.documentsByIds(any(), any())).thenReturn(Map.of());
        VectorStore communityStore = mock(VectorStore.class);

        IngestionService service = new IngestionService(repository, new KnowledgeDocumentConverter(),
                new PlantEntityDocumentConverter(), mock(PlantCatalogIndex.class), sparseIndex,
                mock(VectorStore.class), mock(VectorStore.class), communityStore, mock(VectorStore.class),
                mock(DiseaseKnowledgeRepository.class), mock(DiseaseKnowledgeConverter.class),
                stateRepository, new RagProperties());

        service.indexPost("post-1");

        verify(communityStore, never()).add(anyList());
        verify(sparseIndex).upsertAll(anyList());
    }

    @Test
    void changedIndexFingerprintShouldReEmbedUnchangedPostContent() {
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        KnowledgeRepository.PostRow row = new KnowledgeRepository.PostRow("post-1", "绿萝黄叶记录", "改善通风后恢复。",
                2, 1, 0, 10, false, java.time.Instant.parse("2026-01-01T00:00:00Z"), "绿萝");
        when(repository.findPublishedPost("post-1")).thenReturn(row);

        KnowledgeDocument document = new KnowledgeDocumentConverter().fromPost(row).get(0);
        EmbeddingStateRepository stateRepository = mock(EmbeddingStateRepository.class);
        when(stateRepository.documentIdsBySourceId(KnowledgeSource.COMMUNITY, "post-1")).thenReturn(Set.of());
        IndexFingerprint oldFingerprint = new IndexFingerprint("BAAI/bge-m3", "embedding-content-v1", "chunk-schema-v1");
        when(stateRepository.findByDocumentIds(any())).thenReturn(Map.of(document.id(),
                new EmbeddingStateRepository.EmbeddingState(document.id(), KnowledgeSource.COMMUNITY, "post-1",
                        String.valueOf(document.metadata().get("contentHash")), "BAAI/bge-m3",
                        "embedding-content-v1", "chunk-schema-v1", oldFingerprint.value())));

        SparseIndexService sparseIndex = mock(SparseIndexService.class);
        when(sparseIndex.idsBySourceId(KnowledgeSource.COMMUNITY, "post-1")).thenReturn(Set.of());
        when(sparseIndex.documentsByIds(any(), any())).thenReturn(Map.of());
        VectorStore communityStore = mock(VectorStore.class);

        IngestionService service = new IngestionService(repository, new KnowledgeDocumentConverter(),
                new PlantEntityDocumentConverter(), mock(PlantCatalogIndex.class), sparseIndex,
                mock(VectorStore.class), mock(VectorStore.class), communityStore, mock(VectorStore.class),
                mock(DiseaseKnowledgeRepository.class), mock(DiseaseKnowledgeConverter.class),
                stateRepository, new RagProperties());

        service.indexPost("post-1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> vectorDocuments = ArgumentCaptor.forClass(List.class);
        verify(communityStore).add(vectorDocuments.capture());
        Document vectorDocument = vectorDocuments.getValue().get(0);
        assertThat(vectorDocument.getText()).isEqualTo(document.embeddingText());
        assertThat(vectorDocument.getMetadata()).containsEntry("displayContent", document.displayContent())
                .containsEntry("embeddingContentVersion", "embedding-content-v2")
                .containsEntry("chunkSchemaVersion", "chunk-schema-v2");
        verify(stateRepository).upsertAll(anyList());
    }
}
