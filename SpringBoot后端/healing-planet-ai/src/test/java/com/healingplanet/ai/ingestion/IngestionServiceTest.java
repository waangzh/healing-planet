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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;

class IngestionServiceTest {

    @Test
    void communityIncrementalIndexShouldRunInsideTheCommunitySourceLease() {
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        when(repository.findPublishedPost("post-1")).thenReturn(postRow());
        EmbeddingStateRepository stateRepository = mock(EmbeddingStateRepository.class);
        when(stateRepository.documentIdsBySourceId(KnowledgeSource.COMMUNITY, "post-1")).thenReturn(Set.of());
        when(stateRepository.findByDocumentIds(any())).thenReturn(Map.of());
        SparseIndexService sparseIndex = mock(SparseIndexService.class);
        when(sparseIndex.idsBySourceId(KnowledgeSource.COMMUNITY, "post-1")).thenReturn(Set.of());
        when(sparseIndex.documentsByIds(any(), any())).thenReturn(Map.of());
        SourceIngestionLock sourceLock = mock(SourceIngestionLock.class);
        doAnswer(invocation -> {
            ((SourceIngestionLock.LeaseAction) invocation.getArgument(1))
                    .run(SourceIngestionLock.LeaseGuard.noOp());
            return null;
        }).when(sourceLock).execute(any(), any());

        service(repository, new KnowledgeDocumentConverter(), sparseIndex, mock(VectorStore.class), stateRepository,
                VectorPayloadUpdater.noOp(), sourceLock).indexPost("post-1");

        verify(sourceLock).execute(eq(KnowledgeSource.COMMUNITY), any());
    }

    @Test
    void communityFullScanShouldUseTheSameCommunitySourceLeaseAsIncrementalIndexing() {
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        when(repository.findPublishedPostsAfter(anyString(), anyInt())).thenReturn(List.of());
        EmbeddingStateRepository stateRepository = mock(EmbeddingStateRepository.class);
        when(stateRepository.documentIdsBySource(KnowledgeSource.COMMUNITY)).thenReturn(Set.of());
        SparseIndexService sparseIndex = mock(SparseIndexService.class);
        when(sparseIndex.ids(KnowledgeSource.COMMUNITY)).thenReturn(Set.of());
        SourceIngestionLock sourceLock = mock(SourceIngestionLock.class);
        doAnswer(invocation -> {
            ((SourceIngestionLock.LeaseAction) invocation.getArgument(1))
                    .run(SourceIngestionLock.LeaseGuard.noOp());
            return null;
        }).when(sourceLock).execute(any(), any());

        service(repository, new KnowledgeDocumentConverter(), sparseIndex, mock(VectorStore.class), stateRepository,
                VectorPayloadUpdater.noOp(), sourceLock).indexCommunity();

        verify(sourceLock).execute(eq(KnowledgeSource.COMMUNITY), any());
    }

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
    void changedCommunityAffinityShouldOverwritePayloadWithoutEmbedding() {
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        KnowledgeRepository.PostRow row = postRow();
        when(repository.findPublishedPost("post-1")).thenReturn(row);
        KnowledgeDocument initial = communityDocument("plant-1", 10, 100);
        KnowledgeDocument affinityChanged = communityDocument("plant-2", 10, 100);
        KnowledgeDocumentConverter converter = mock(KnowledgeDocumentConverter.class);
        when(converter.fromPost(row)).thenReturn(List.of(initial), List.of(affinityChanged));
        EmbeddingStateRepository stateRepository = mock(EmbeddingStateRepository.class);
        when(stateRepository.documentIdsBySourceId(KnowledgeSource.COMMUNITY, "post-1")).thenReturn(Set.of());
        when(stateRepository.findByDocumentIds(any())).thenReturn(Map.of());
        SparseIndexService sparseIndex = mock(SparseIndexService.class);
        when(sparseIndex.idsBySourceId(KnowledgeSource.COMMUNITY, "post-1")).thenReturn(Set.of());
        when(sparseIndex.documentsByIds(any(), any())).thenReturn(Map.of());
        VectorStore communityStore = mock(VectorStore.class);
        VectorPayloadUpdater payloadUpdater = mock(VectorPayloadUpdater.class);
        IngestionService service = service(repository, converter, sparseIndex, communityStore, stateRepository, payloadUpdater);

        service.indexPost("post-1");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EmbeddingStateRepository.EmbeddingState>> states = ArgumentCaptor.forClass(List.class);
        verify(stateRepository).upsertAll(states.capture());
        EmbeddingStateRepository.EmbeddingState state = states.getValue().get(0);
        clearInvocations(communityStore, payloadUpdater, sparseIndex, stateRepository);
        when(stateRepository.findByDocumentIds(any())).thenReturn(Map.of(initial.id(), state));
        when(sparseIndex.documentsByIds(any(), any())).thenReturn(Map.of(initial.id(), initial));

        service.indexPost("post-1");

        verify(communityStore, never()).add(anyList());
        verify(payloadUpdater).overwritePayloads(eq(KnowledgeSource.COMMUNITY), org.mockito.ArgumentMatchers.argThat(
                documents -> documents.size() == 1 && "plant-2".equals(
                        documents.get(0).attributes().get("resolvedPlantIds"))));
        verify(sparseIndex).upsertAll(anyList());
        verify(stateRepository).upsertAll(anyList());
    }

    @Test
    void changedCommunityEngagementShouldHydrateAtQueryTimeWithoutAnyIndexWrite() {
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        KnowledgeRepository.PostRow row = postRow();
        when(repository.findPublishedPost("post-1")).thenReturn(row);
        KnowledgeDocument initial = communityDocument("plant-1", 10, 100);
        KnowledgeDocument engagementChanged = communityDocument("plant-1", 11, 101);
        KnowledgeDocumentConverter converter = mock(KnowledgeDocumentConverter.class);
        when(converter.fromPost(row)).thenReturn(List.of(initial), List.of(engagementChanged));
        EmbeddingStateRepository stateRepository = mock(EmbeddingStateRepository.class);
        when(stateRepository.documentIdsBySourceId(KnowledgeSource.COMMUNITY, "post-1")).thenReturn(Set.of());
        when(stateRepository.findByDocumentIds(any())).thenReturn(Map.of());
        SparseIndexService sparseIndex = mock(SparseIndexService.class);
        when(sparseIndex.idsBySourceId(KnowledgeSource.COMMUNITY, "post-1")).thenReturn(Set.of());
        when(sparseIndex.documentsByIds(any(), any())).thenReturn(Map.of());
        VectorStore communityStore = mock(VectorStore.class);
        VectorPayloadUpdater payloadUpdater = mock(VectorPayloadUpdater.class);
        IngestionService service = service(repository, converter, sparseIndex, communityStore, stateRepository, payloadUpdater);

        service.indexPost("post-1");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EmbeddingStateRepository.EmbeddingState>> states = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<KnowledgeDocument>> sparseDocuments = ArgumentCaptor.forClass(List.class);
        verify(stateRepository).upsertAll(states.capture());
        verify(sparseIndex).upsertAll(sparseDocuments.capture());
        EmbeddingStateRepository.EmbeddingState state = states.getValue().get(0);
        KnowledgeDocument indexedDocument = sparseDocuments.getValue().get(0);
        clearInvocations(communityStore, payloadUpdater, sparseIndex, stateRepository);
        when(stateRepository.findByDocumentIds(any())).thenReturn(Map.of(initial.id(), state));
        when(sparseIndex.documentsByIds(any(), any())).thenReturn(Map.of(initial.id(), indexedDocument));

        service.indexPost("post-1");

        verify(communityStore, never()).add(anyList());
        verify(payloadUpdater, never()).overwritePayloads(any(), anyList());
        verify(sparseIndex, never()).upsertAll(anyList());
        verify(stateRepository, never()).upsertAll(anyList());
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

    @Test
    void shouldReportNewFragmentsAndTheReasonForEmbedding() {
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        KnowledgeRepository.PostRow row = postRow();
        when(repository.findPublishedPost("post-1")).thenReturn(row);
        EmbeddingStateRepository stateRepository = mock(EmbeddingStateRepository.class);
        when(stateRepository.documentIdsBySourceId(KnowledgeSource.COMMUNITY, "post-1")).thenReturn(Set.of());
        when(stateRepository.findByDocumentIds(any())).thenReturn(Map.of());
        SparseIndexService sparseIndex = mock(SparseIndexService.class);
        when(sparseIndex.idsBySourceId(KnowledgeSource.COMMUNITY, "post-1")).thenReturn(Set.of());
        when(sparseIndex.documentsByIds(any(), any())).thenReturn(Map.of());

        IngestionService service = new IngestionService(repository, new KnowledgeDocumentConverter(),
                new PlantEntityDocumentConverter(), mock(PlantCatalogIndex.class), sparseIndex,
                mock(VectorStore.class), mock(VectorStore.class), mock(VectorStore.class), mock(VectorStore.class),
                mock(DiseaseKnowledgeRepository.class), mock(DiseaseKnowledgeConverter.class), stateRepository,
                new RagProperties());

        var report = service.indexPost("post-1");

        assertThat(report.status()).isEqualTo(com.healingplanet.ai.domain.IndexRunReport.Status.SUCCEEDED);
        assertThat(report.documentsSeen()).isEqualTo(1);
        assertThat(report.documentsEmbedded()).isEqualTo(1);
        assertThat(report.fragmentsCreated()).isEqualTo(1);
        assertThat(report.logicalEvidencesCreated()).isEqualTo(1);
        assertThat(report.reembedReasons()).containsEntry("new_document", 1);
        assertThat(report.sources()).singleElement().satisfies(source ->
                assertThat(source.source()).isEqualTo(KnowledgeSource.COMMUNITY));
    }

    @Test
    void sourceShouldBeMarkedFailedWhenTheLeaseCannotBeAcquired() {
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        IndexRunStatusStore statusStore = mock(IndexRunStatusStore.class);
        SourceIngestionLock sourceLock = (source, action) -> {
            throw new SourceIngestionLeaseException("等待 " + source + " 索引租约超时");
        };

        IngestionService service = serviceWithStatusStore(repository, mock(SparseIndexService.class),
                mock(EmbeddingStateRepository.class), statusStore, sourceLock);

        assertThatThrownBy(() -> service.indexCommunity())
                .isInstanceOf(SourceIngestionLeaseException.class)
                .hasMessageContaining("等待 COMMUNITY 索引租约超时");

        verify(statusStore).markRunning(eq(KnowledgeSource.COMMUNITY), anyString(),
                eq(com.healingplanet.ai.domain.IndexOperation.COMMUNITY), any(), anyString());
        verify(statusStore).markFailed(eq(KnowledgeSource.COMMUNITY), anyString(),
                eq(com.healingplanet.ai.domain.IndexOperation.COMMUNITY), any(), any(), anyString(), eq(0),
                eq("等待 COMMUNITY 索引租约超时"));
        verify(statusStore, never()).markSucceeded(any(), anyString(), any(), any(), any(), anyString());
    }

    @Test
    void sourceShouldNotBeMarkedSucceededWhenTheLeaseIsLostAfterIndexWork() {
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        when(repository.findPublishedPostsAfter(anyString(), anyInt())).thenReturn(List.of());
        EmbeddingStateRepository stateRepository = mock(EmbeddingStateRepository.class);
        when(stateRepository.documentIdsBySource(KnowledgeSource.COMMUNITY)).thenReturn(Set.of());
        SparseIndexService sparseIndex = mock(SparseIndexService.class);
        when(sparseIndex.ids(KnowledgeSource.COMMUNITY)).thenReturn(Set.of());
        IndexRunStatusStore statusStore = mock(IndexRunStatusStore.class);
        SourceIngestionLock sourceLock = (source, action) -> {
            action.run(SourceIngestionLock.LeaseGuard.noOp());
            throw new SourceIngestionLeaseException(source + " 索引租约已丢失");
        };

        IngestionService service = serviceWithStatusStore(repository, sparseIndex, stateRepository, statusStore, sourceLock);

        assertThatThrownBy(() -> service.indexCommunity())
                .isInstanceOf(SourceIngestionLeaseException.class)
                .hasMessageContaining("COMMUNITY 索引租约已丢失");

        verify(statusStore).markRunning(eq(KnowledgeSource.COMMUNITY), anyString(),
                eq(com.healingplanet.ai.domain.IndexOperation.COMMUNITY), any(), anyString());
        verify(statusStore).markFailed(eq(KnowledgeSource.COMMUNITY), anyString(),
                eq(com.healingplanet.ai.domain.IndexOperation.COMMUNITY), any(), any(), anyString(), eq(0),
                eq("COMMUNITY 索引租约已丢失"));
        verify(statusStore, never()).markSucceeded(any(), anyString(), any(), any(), any(), anyString());
    }

    private IngestionService service(KnowledgeRepository repository, KnowledgeDocumentConverter converter,
                                     SparseIndexService sparseIndex, VectorStore communityStore,
                                     EmbeddingStateRepository stateRepository, VectorPayloadUpdater payloadUpdater) {
        return service(repository, converter, sparseIndex, communityStore, stateRepository, payloadUpdater,
                SourceIngestionLock.noOp());
    }

    private IngestionService service(KnowledgeRepository repository, KnowledgeDocumentConverter converter,
                                     SparseIndexService sparseIndex, VectorStore communityStore,
                                     EmbeddingStateRepository stateRepository, VectorPayloadUpdater payloadUpdater,
                                     SourceIngestionLock sourceIngestionLock) {
        return new IngestionService(repository, converter, new PlantEntityDocumentConverter(),
                mock(PlantCatalogIndex.class), sparseIndex, mock(VectorStore.class), mock(VectorStore.class),
                communityStore, mock(VectorStore.class), mock(DiseaseKnowledgeRepository.class),
                mock(DiseaseKnowledgeConverter.class), stateRepository, new RagProperties(), payloadUpdater,
                sourceIngestionLock);
    }

    private IngestionService serviceWithStatusStore(KnowledgeRepository repository, SparseIndexService sparseIndex,
                                                     EmbeddingStateRepository stateRepository,
                                                     IndexRunStatusStore statusStore,
                                                     SourceIngestionLock sourceIngestionLock) {
        return new IngestionService(repository, new KnowledgeDocumentConverter(), new PlantEntityDocumentConverter(),
                mock(PlantCatalogIndex.class), sparseIndex, mock(VectorStore.class), mock(VectorStore.class),
                mock(VectorStore.class), mock(VectorStore.class), mock(DiseaseKnowledgeRepository.class),
                mock(DiseaseKnowledgeConverter.class), stateRepository, new RagProperties(), VectorPayloadUpdater.noOp(),
                statusStore, IndexMetrics.noOp(), Clock.fixed(Instant.parse("2026-09-02T00:00:00Z"), ZoneOffset.UTC),
                sourceIngestionLock);
    }

    private KnowledgeRepository.PostRow postRow() {
        return new KnowledgeRepository.PostRow("post-1", "绿萝黄叶记录", "改善通风后恢复。",
                0, 0, 0, 0, false, java.time.Instant.parse("2026-01-01T00:00:00Z"), "绿萝");
    }

    private KnowledgeDocument communityDocument(String resolvedPlantId, int likes, int views) {
        return new KnowledgeDocument("6f1eb1f1-7e70-45dc-af62-c139c85a177e", KnowledgeSource.COMMUNITY,
                "post-1", "绿萝黄叶记录", "标题：绿萝黄叶记录\n正文：改善通风后恢复。", "改善通风后恢复。", "", "绿萝",
                "COMMUNITY_EXPERIENCE", List.of("绿萝"), 0.5, false, likes, 1, 2, views,
                java.time.Instant.parse("2026-01-01T00:00:00Z"), Map.of("indexVersion", "logical-evidence-v2",
                "resolvedPlantIds", resolvedPlantId, "plantEntityConfidence", "1.0"));
    }
}
