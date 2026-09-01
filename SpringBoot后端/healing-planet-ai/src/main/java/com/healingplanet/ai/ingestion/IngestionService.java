package com.healingplanet.ai.ingestion;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.domain.IndexReport;
import com.healingplanet.ai.domain.KnowledgeDocument;
import com.healingplanet.ai.domain.KnowledgeSource;
import com.healingplanet.ai.retrieval.SparseIndexService;
import com.healingplanet.ai.retrieval.PlantCatalogIndex;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

@Service
public class IngestionService {

    private final KnowledgeRepository repository;
    private final KnowledgeDocumentConverter converter;
    private final PlantEntityDocumentConverter entityConverter;
    private final PlantCatalogIndex plantCatalogIndex;
    private final SparseIndexService sparseIndex;
    private final VectorStore plantVectorStore;
    private final VectorStore plantEntityVectorStore;
    private final VectorStore communityVectorStore;
    private final VectorStore diseaseVectorStore;
    private final DiseaseKnowledgeRepository diseaseRepository;
    private final DiseaseKnowledgeConverter diseaseConverter;
    private final EmbeddingStateRepository embeddingStateRepository;
    private final RagProperties properties;
    private final VectorPayloadUpdater payloadUpdater;

    /** Compatibility constructor for focused tests and callers that do not provide a Qdrant payload adapter. */
    public IngestionService(KnowledgeRepository repository, KnowledgeDocumentConverter converter,
                            PlantEntityDocumentConverter entityConverter,
                            PlantCatalogIndex plantCatalogIndex,
                            SparseIndexService sparseIndex,
                            @Qualifier("plantVectorStore") VectorStore plantVectorStore,
                            @Qualifier("plantEntityVectorStore") VectorStore plantEntityVectorStore,
                            @Qualifier("communityVectorStore") VectorStore communityVectorStore,
                            @Qualifier("diseaseVectorStore") VectorStore diseaseVectorStore,
                            DiseaseKnowledgeRepository diseaseRepository,
                            DiseaseKnowledgeConverter diseaseConverter,
                            EmbeddingStateRepository embeddingStateRepository,
                            RagProperties properties) {
        this(repository, converter, entityConverter, plantCatalogIndex, sparseIndex, plantVectorStore,
                plantEntityVectorStore, communityVectorStore, diseaseVectorStore, diseaseRepository, diseaseConverter,
                embeddingStateRepository, properties, VectorPayloadUpdater.noOp());
    }

    @Autowired
    public IngestionService(KnowledgeRepository repository, KnowledgeDocumentConverter converter,
                            PlantEntityDocumentConverter entityConverter,
                            PlantCatalogIndex plantCatalogIndex,
                            SparseIndexService sparseIndex,
                            @Qualifier("plantVectorStore") VectorStore plantVectorStore,
                            @Qualifier("plantEntityVectorStore") VectorStore plantEntityVectorStore,
                            @Qualifier("communityVectorStore") VectorStore communityVectorStore,
                            @Qualifier("diseaseVectorStore") VectorStore diseaseVectorStore,
                            DiseaseKnowledgeRepository diseaseRepository,
                            DiseaseKnowledgeConverter diseaseConverter,
                            EmbeddingStateRepository embeddingStateRepository,
                            RagProperties properties,
                            VectorPayloadUpdater payloadUpdater) {
        this.repository = repository;
        this.converter = converter;
        this.entityConverter = entityConverter;
        this.plantCatalogIndex = plantCatalogIndex;
        this.sparseIndex = sparseIndex;
        this.plantVectorStore = plantVectorStore;
        this.plantEntityVectorStore = plantEntityVectorStore;
        this.communityVectorStore = communityVectorStore;
        this.diseaseVectorStore = diseaseVectorStore;
        this.diseaseRepository = diseaseRepository;
        this.diseaseConverter = diseaseConverter;
        this.embeddingStateRepository = embeddingStateRepository;
        this.properties = properties;
        this.payloadUpdater = payloadUpdater;
    }

    public IndexReport fullIndex() {
        IndexReport plant = indexPlants();
        IndexReport community = indexCommunity();
        IndexReport disease = indexDiseases();
        return new IndexReport(plant.plantDocuments(), community.communityDocuments(), disease.diseaseDocuments(),
                plant.deletedDocuments() + community.deletedDocuments() + disease.deletedDocuments());
    }

    public IndexReport indexPlants() {
        IndexingResult entities = indexPaged(KnowledgeSource.PLANT_ENTITY, plantEntityVectorStore,
                repository::findPlantEntitiesAfter, row -> List.of(entityConverter.convert(row)),
                KnowledgeRepository.PlantEntityRow::id);
        plantCatalogIndex.refresh();

        IndexingResult plants = indexPaged(KnowledgeSource.PLANT, plantVectorStore,
                repository::findPlantsAfter, converter::fromPlant, KnowledgeRepository.PlantRow::id);
        return IndexReport.plant(plants.documents(), entities.deleted() + plants.deleted());
    }

    public IndexReport indexCommunity() {
        IndexingResult community = indexPaged(KnowledgeSource.COMMUNITY, communityVectorStore,
                repository::findPublishedPostsAfter, converter::fromPost, KnowledgeRepository.PostRow::id);
        return IndexReport.community(community.documents(), community.deleted());
    }

    public IndexReport indexDiseases() {
        IndexingResult disease = indexPaged(KnowledgeSource.DISEASE, diseaseVectorStore,
                diseaseRepository::findAfter, diseaseConverter::convertAll, DiseaseKnowledgeRepository.DiseaseRow::id);
        return IndexReport.disease(disease.documents(), disease.deleted());
    }

    public IndexReport indexDisease(String diseaseId) {
        Set<String> oldIds = existingIdsBySourceId(KnowledgeSource.DISEASE, diseaseId);
        DiseaseKnowledgeRepository.DiseaseRow row = diseaseRepository.findById(diseaseId);
        if (row == null) {
            deleteIds(KnowledgeSource.DISEASE, oldIds, diseaseVectorStore);
            return IndexReport.disease(0, oldIds.size());
        }
        List<KnowledgeDocument> documents = prepare(diseaseConverter.convertAll(row));
        Set<String> newIds = documentIds(documents);
        Set<String> staleIds = new HashSet<>(oldIds);
        staleIds.removeAll(newIds);
        deleteIds(KnowledgeSource.DISEASE, staleIds, diseaseVectorStore);
        syncBatch(KnowledgeSource.DISEASE, documents, diseaseVectorStore);
        return IndexReport.disease(documents.size(), staleIds.size());
    }

    public IndexReport indexPost(String postId) {
        Set<String> oldIds = existingIdsBySourceId(KnowledgeSource.COMMUNITY, postId);
        KnowledgeRepository.PostRow row = repository.findPublishedPost(postId);
        if (row == null) {
            deleteIds(KnowledgeSource.COMMUNITY, oldIds, communityVectorStore);
            return IndexReport.community(0, oldIds.size());
        }
        List<KnowledgeDocument> documents = prepare(converter.fromPost(row));
        Set<String> newIds = documentIds(documents);
        Set<String> staleIds = new HashSet<>(oldIds);
        staleIds.removeAll(newIds);
        deleteIds(KnowledgeSource.COMMUNITY, staleIds, communityVectorStore);
        syncBatch(KnowledgeSource.COMMUNITY, documents, communityVectorStore);
        return IndexReport.community(documents.size(), staleIds.size());
    }

    public IndexReport deletePost(String postId) {
        Set<String> ids = existingIdsBySourceId(KnowledgeSource.COMMUNITY, postId);
        deleteIds(KnowledgeSource.COMMUNITY, ids, communityVectorStore);
        return IndexReport.community(0, ids.size());
    }

    private <T> IndexingResult indexPaged(KnowledgeSource source, VectorStore vectorStore,
                                           BiFunction<String, Integer, List<T>> pageFetcher,
                                           Function<T, List<KnowledgeDocument>> documentConverter,
                                           Function<T, String> rowId) {
        Set<String> staleIds = existingIds(source);
        String lastId = "";
        int documents = 0;
        int batchSize = batchSize();

        while (true) {
            List<T> rows = pageFetcher.apply(lastId, batchSize);
            if (rows.isEmpty()) {
                break;
            }
            List<KnowledgeDocument> batch = prepare(rows.stream()
                    .flatMap(row -> documentConverter.apply(row).stream()).toList());
            syncBatch(source, batch, vectorStore);
            documents += batch.size();
            staleIds.removeAll(documentIds(batch));
            lastId = rowId.apply(rows.get(rows.size() - 1));
            if (rows.size() < batchSize) {
                break;
            }
        }
        deleteIds(source, staleIds, vectorStore);
        return new IndexingResult(documents, staleIds.size());
    }

    private void syncBatch(KnowledgeSource source, List<KnowledgeDocument> documents, VectorStore vectorStore) {
        if (documents.isEmpty()) {
            return;
        }
        Set<String> ids = documentIds(documents);
        Map<String, EmbeddingStateRepository.EmbeddingState> states = embeddingStateRepository.findByDocumentIds(ids);
        IndexFingerprint fingerprint = indexFingerprint();
        List<KnowledgeDocument> documentsToEmbed = documents.stream()
                .filter(document -> needsEmbedding(document, states.get(document.id()), fingerprint)).toList();
        if (!documentsToEmbed.isEmpty()) {
            vectorStore.add(toSpringDocuments(documentsToEmbed));
            embeddingStateRepository.upsertAll(documentsToEmbed.stream()
                    .map(document -> toEmbeddingState(document, fingerprint)).toList());
        }

        List<KnowledgeDocument> payloadUpdates = documents.stream()
                .filter(document -> !documentsToEmbed.contains(document))
                .filter(document -> needsPayloadUpdate(document, states.get(document.id()))).toList();
        if (!payloadUpdates.isEmpty()) {
            payloadUpdater.overwritePayloads(source, payloadUpdates);
            embeddingStateRepository.upsertAll(payloadUpdates.stream()
                    .map(document -> toEmbeddingState(document, fingerprint)).toList());
        }

        Map<String, KnowledgeDocument> sparseDocuments = sparseIndex.documentsByIds(source, ids);
        List<KnowledgeDocument> sparseUpdates = documents.stream()
                .filter(document -> needsSparseUpdate(document, sparseDocuments.get(document.id()))).toList();
        if (!sparseUpdates.isEmpty()) {
            sparseIndex.upsertAll(sparseUpdates);
        }
    }

    private List<KnowledgeDocument> prepare(List<KnowledgeDocument> documents) {
        IndexFingerprint fingerprint = indexFingerprint();
        return documents.stream().map(document -> withEmbeddingMetadata(document, fingerprint)).toList();
    }

    private KnowledgeDocument withEmbeddingMetadata(KnowledgeDocument document, IndexFingerprint fingerprint) {
        Map<String, String> attributes = new LinkedHashMap<>(document.attributes());
        attributes.put("contentHash", contentHash(document));
        attributes.put("embeddingModelVersion", fingerprint.embeddingModelVersion());
        attributes.put("embeddingContentVersion", fingerprint.embeddingContentVersion());
        attributes.put("chunkSchemaVersion", fingerprint.chunkSchemaVersion());
        attributes.put("indexFingerprint", fingerprint.value());
        attributes.put("payloadHash", payloadHash(document));
        return new KnowledgeDocument(document.id(), document.source(), document.sourceId(), document.title(),
                document.embeddingText(), document.displayContent(), document.canonicalPlantId(), document.plantName(),
                document.knowledgeType(),
                document.tags(), document.trustScore(), document.essence(), document.likes(), document.collects(),
                document.comments(), document.views(), document.createdAt(), attributes);
    }

    private boolean needsEmbedding(KnowledgeDocument document, EmbeddingStateRepository.EmbeddingState state,
                                   IndexFingerprint fingerprint) {
        return state == null
                || !document.attributes().get("contentHash").equals(state.contentHash())
                || !fingerprint.value().equals(state.indexFingerprint());
    }

    private boolean needsPayloadUpdate(KnowledgeDocument document, EmbeddingStateRepository.EmbeddingState state) {
        return state == null || !document.attributes().get("payloadHash").equals(state.payloadHash());
    }

    private boolean needsSparseUpdate(KnowledgeDocument document, KnowledgeDocument existing) {
        return existing == null || !sparseCompatibilityHash(document).equals(sparseCompatibilityHash(existing));
    }

    private EmbeddingStateRepository.EmbeddingState toEmbeddingState(KnowledgeDocument document,
                                                                      IndexFingerprint fingerprint) {
        return new EmbeddingStateRepository.EmbeddingState(document.id(), document.source(), document.sourceId(),
                document.attributes().get("contentHash"), fingerprint.embeddingModelVersion(),
                fingerprint.embeddingContentVersion(), fingerprint.chunkSchemaVersion(), fingerprint.value(),
                document.attributes().get("payloadHash"));
    }

    private Set<String> existingIds(KnowledgeSource source) {
        Set<String> ids = new HashSet<>(embeddingStateRepository.documentIdsBySource(source));
        ids.addAll(sparseIndex.ids(source));
        return ids;
    }

    private Set<String> existingIdsBySourceId(KnowledgeSource source, String sourceId) {
        Set<String> ids = new HashSet<>(embeddingStateRepository.documentIdsBySourceId(source, sourceId));
        ids.addAll(sparseIndex.idsBySourceId(source, sourceId));
        return ids;
    }

    private Set<String> documentIds(List<KnowledgeDocument> documents) {
        return documents.stream().map(KnowledgeDocument::id).collect(java.util.stream.Collectors.toSet());
    }

    private int batchSize() {
        int size = properties.getIngestion().getBatchSize();
        if (size < 50 || size > 200) {
            throw new IllegalStateException("app.rag.ingestion.batch-size 必须在 50 到 200 之间");
        }
        return size;
    }

    private IndexFingerprint indexFingerprint() {
        var ingestion = properties.getIngestion();
        return new IndexFingerprint(ingestion.getEmbeddingModelVersion(), ingestion.getEmbeddingContentVersion(),
                ingestion.getChunkSchemaVersion());
    }

    private void deleteIds(KnowledgeSource source, Set<String> ids, VectorStore vectorStore) {
        if (ids.isEmpty()) return;
        List<String> allIds = new ArrayList<>(ids);
        int batchSize = batchSize();
        for (int start = 0; start < allIds.size(); start += batchSize) {
            List<String> batch = allIds.subList(start, Math.min(start + batchSize, allIds.size()));
            vectorStore.delete(batch);
            sparseIndex.deleteAll(source, batch);
            embeddingStateRepository.deleteByDocumentIds(batch);
        }
    }

    private List<Document> toSpringDocuments(List<KnowledgeDocument> documents) {
        return documents.stream().map(document -> new Document(
                document.id(), document.embeddingText(), document.vectorPayloadMetadata())).toList();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", exception);
        }
    }

    private String contentHash(KnowledgeDocument document) {
        String indexVersion = document.attributes().getOrDefault("indexVersion", "");
        String value = indexVersion.isBlank() ? document.embeddingText()
                : indexVersion + "\u0000" + document.embeddingText();
        return sha256(value);
    }

    private String payloadHash(KnowledgeDocument document) {
        return hashMap(document.vectorPayloadMetadata());
    }

    private String sparseCompatibilityHash(KnowledgeDocument document) {
        return sha256(document.id() + "\u0000" + document.embeddingText() + "\u0000" + document.displayContent()
                + "\u0000" + hashMap(document.vectorPayloadMetadata()));
    }

    private String hashMap(Map<String, Object> values) {
        String value = values.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "\u0000" + String.valueOf(entry.getValue()))
                .collect(java.util.stream.Collectors.joining("\u0001"));
        return sha256(value);
    }

    private record IndexingResult(int documents, int deleted) {
    }
}
