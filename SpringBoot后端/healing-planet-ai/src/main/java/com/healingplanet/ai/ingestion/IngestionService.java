package com.healingplanet.ai.ingestion;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.domain.IndexOperation;
import com.healingplanet.ai.domain.IndexRunReport;
import com.healingplanet.ai.domain.KnowledgeDocument;
import com.healingplanet.ai.domain.KnowledgeSource;
import com.healingplanet.ai.domain.SourceIndexRunReport;
import com.healingplanet.ai.retrieval.SparseIndexService;
import com.healingplanet.ai.retrieval.PlantCatalogIndex;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.time.Clock;
import java.time.Instant;
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
import java.util.function.Consumer;
import java.util.UUID;

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
    private final IndexRunStatusStore indexRunStatusStore;
    private final IndexMetrics indexMetrics;
    private final Clock clock;
    private final SourceIngestionLock sourceIngestionLock;

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
                embeddingStateRepository, properties, VectorPayloadUpdater.noOp(), IndexRunStatusStore.noOp(),
                IndexMetrics.noOp(), Clock.systemUTC(), SourceIngestionLock.noOp());
    }

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
        this(repository, converter, entityConverter, plantCatalogIndex, sparseIndex, plantVectorStore,
                plantEntityVectorStore, communityVectorStore, diseaseVectorStore, diseaseRepository, diseaseConverter,
                embeddingStateRepository, properties, payloadUpdater, IndexRunStatusStore.noOp(), IndexMetrics.noOp(),
                Clock.systemUTC(), SourceIngestionLock.noOp());
    }

    /** Test-focused constructor for verifying source serialization without wiring persistent run telemetry. */
    IngestionService(KnowledgeRepository repository, KnowledgeDocumentConverter converter,
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
                     VectorPayloadUpdater payloadUpdater,
                     SourceIngestionLock sourceIngestionLock) {
        this(repository, converter, entityConverter, plantCatalogIndex, sparseIndex, plantVectorStore,
                plantEntityVectorStore, communityVectorStore, diseaseVectorStore, diseaseRepository, diseaseConverter,
                embeddingStateRepository, properties, payloadUpdater, IndexRunStatusStore.noOp(), IndexMetrics.noOp(),
                Clock.systemUTC(), sourceIngestionLock);
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
                            VectorPayloadUpdater payloadUpdater,
                            IndexRunStatusStore indexRunStatusStore,
                            IndexMetrics indexMetrics,
                            @Qualifier("ragClock") Clock clock,
                            SourceIngestionLock sourceIngestionLock) {
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
        this.indexRunStatusStore = indexRunStatusStore;
        this.indexMetrics = indexMetrics;
        this.clock = clock;
        this.sourceIngestionLock = sourceIngestionLock;
    }

    public IndexRunReport fullIndex() {
        return run(IndexOperation.FULL, context -> {
            indexPlants(context);
            indexCommunity(context);
            indexDiseases(context);
        });
    }

    public IndexRunReport indexPlants() {
        return run(IndexOperation.PLANTS, this::indexPlants);
    }

    public IndexRunReport indexCommunity() {
        return run(IndexOperation.COMMUNITY, this::indexCommunity);
    }

    public IndexRunReport indexDiseases() {
        return run(IndexOperation.DISEASES, this::indexDiseases);
    }

    public IndexRunReport indexDisease(String diseaseId) {
        return run(IndexOperation.DISEASE_UPSERT, context -> sourceIngestionLock.execute(KnowledgeSource.DISEASE, () -> {
            SourceRunCounters counters = context.begin(KnowledgeSource.DISEASE);
            Set<String> oldIds = existingIdsBySourceId(KnowledgeSource.DISEASE, diseaseId);
            DiseaseKnowledgeRepository.DiseaseRow row = diseaseRepository.findById(diseaseId);
            if (row == null) {
                counters.documentsDeleted += deleteIds(KnowledgeSource.DISEASE, oldIds, diseaseVectorStore);
            } else {
                List<KnowledgeDocument> documents = prepare(diseaseConverter.convertAll(row));
                Set<String> newIds = documentIds(documents);
                Set<String> staleIds = new HashSet<>(oldIds);
                staleIds.removeAll(newIds);
                counters.documentsDeleted += deleteIds(KnowledgeSource.DISEASE, staleIds, diseaseVectorStore);
                syncBatch(KnowledgeSource.DISEASE, documents, diseaseVectorStore, counters);
            }
            context.complete(KnowledgeSource.DISEASE);
        }));
    }

    public IndexRunReport indexPost(String postId) {
        return run(IndexOperation.POST_UPSERT, context -> sourceIngestionLock.execute(KnowledgeSource.COMMUNITY, () -> {
            SourceRunCounters counters = context.begin(KnowledgeSource.COMMUNITY);
            Set<String> oldIds = existingIdsBySourceId(KnowledgeSource.COMMUNITY, postId);
            KnowledgeRepository.PostRow row = repository.findPublishedPost(postId);
            if (row == null) {
                counters.documentsDeleted += deleteIds(KnowledgeSource.COMMUNITY, oldIds, communityVectorStore);
            } else {
                List<KnowledgeDocument> documents = prepare(converter.fromPost(row));
                Set<String> newIds = documentIds(documents);
                Set<String> staleIds = new HashSet<>(oldIds);
                staleIds.removeAll(newIds);
                counters.documentsDeleted += deleteIds(KnowledgeSource.COMMUNITY, staleIds, communityVectorStore);
                syncBatch(KnowledgeSource.COMMUNITY, documents, communityVectorStore, counters);
            }
            context.complete(KnowledgeSource.COMMUNITY);
        }));
    }

    public IndexRunReport deletePost(String postId) {
        return run(IndexOperation.POST_DELETE, context -> sourceIngestionLock.execute(KnowledgeSource.COMMUNITY, () -> {
            SourceRunCounters counters = context.begin(KnowledgeSource.COMMUNITY);
            Set<String> ids = existingIdsBySourceId(KnowledgeSource.COMMUNITY, postId);
            counters.documentsDeleted += deleteIds(KnowledgeSource.COMMUNITY, ids, communityVectorStore);
            context.complete(KnowledgeSource.COMMUNITY);
        }));
    }

    private void indexPlants(IndexRunContext context) {
        sourceIngestionLock.execute(KnowledgeSource.PLANT_ENTITY, () -> {
            SourceRunCounters entities = context.begin(KnowledgeSource.PLANT_ENTITY);
            indexPaged(KnowledgeSource.PLANT_ENTITY, plantEntityVectorStore, repository::findPlantEntitiesAfter,
                    row -> List.of(entityConverter.convert(row)), KnowledgeRepository.PlantEntityRow::id, entities);
            context.complete(KnowledgeSource.PLANT_ENTITY);
        });
        plantCatalogIndex.refresh();

        sourceIngestionLock.execute(KnowledgeSource.PLANT, () -> {
            SourceRunCounters plants = context.begin(KnowledgeSource.PLANT);
            indexPaged(KnowledgeSource.PLANT, plantVectorStore, repository::findPlantsAfter, converter::fromPlant,
                    KnowledgeRepository.PlantRow::id, plants);
            context.complete(KnowledgeSource.PLANT);
        });
    }

    private void indexCommunity(IndexRunContext context) {
        sourceIngestionLock.execute(KnowledgeSource.COMMUNITY, () -> {
            SourceRunCounters community = context.begin(KnowledgeSource.COMMUNITY);
            indexPaged(KnowledgeSource.COMMUNITY, communityVectorStore, repository::findPublishedPostsAfter,
                    converter::fromPost, KnowledgeRepository.PostRow::id, community);
            context.complete(KnowledgeSource.COMMUNITY);
        });
    }

    private void indexDiseases(IndexRunContext context) {
        sourceIngestionLock.execute(KnowledgeSource.DISEASE, () -> {
            SourceRunCounters disease = context.begin(KnowledgeSource.DISEASE);
            indexPaged(KnowledgeSource.DISEASE, diseaseVectorStore, diseaseRepository::findAfter,
                    diseaseConverter::convertAll, DiseaseKnowledgeRepository.DiseaseRow::id, disease);
            context.complete(KnowledgeSource.DISEASE);
        });
    }

    private <T> void indexPaged(KnowledgeSource source, VectorStore vectorStore,
                                           BiFunction<String, Integer, List<T>> pageFetcher,
                                           Function<T, List<KnowledgeDocument>> documentConverter,
                                           Function<T, String> rowId, SourceRunCounters counters) {
        Set<String> staleIds = existingIds(source);
        String lastId = "";
        int batchSize = batchSize();

        while (true) {
            List<T> rows = pageFetcher.apply(lastId, batchSize);
            if (rows.isEmpty()) {
                break;
            }
            List<KnowledgeDocument> batch = prepare(rows.stream()
                    .flatMap(row -> documentConverter.apply(row).stream()).toList());
            syncBatch(source, batch, vectorStore, counters);
            staleIds.removeAll(documentIds(batch));
            lastId = rowId.apply(rows.get(rows.size() - 1));
            if (rows.size() < batchSize) {
                break;
            }
        }
        counters.documentsDeleted += deleteIds(source, staleIds, vectorStore);
    }

    private void syncBatch(KnowledgeSource source, List<KnowledgeDocument> documents, VectorStore vectorStore,
                           SourceRunCounters counters) {
        if (documents.isEmpty()) {
            return;
        }
        Set<String> ids = documentIds(documents);
        Map<String, EmbeddingStateRepository.EmbeddingState> states = embeddingStateRepository.findByDocumentIds(ids);
        IndexFingerprint fingerprint = indexFingerprint();
        List<KnowledgeDocument> documentsToEmbed = documents.stream()
                .filter(document -> needsEmbedding(document, states.get(document.id()), fingerprint)).toList();
        counters.documentsSeen += documents.size();
        documents.forEach(document -> counters.recordNonEmbeddingDecision(document, states.get(document.id()), fingerprint));
        if (!documentsToEmbed.isEmpty()) {
            try {
                vectorStore.add(toSpringDocuments(documentsToEmbed));
                embeddingStateRepository.upsertAll(documentsToEmbed.stream()
                        .map(document -> toEmbeddingState(document, fingerprint)).toList());
                documentsToEmbed.forEach(document ->
                        counters.recordEmbedded(document, states.get(document.id()), fingerprint));
            } catch (RuntimeException exception) {
                counters.failedDocuments += documentsToEmbed.size();
                throw exception;
            }
        }

        List<KnowledgeDocument> payloadUpdates = documents.stream()
                .filter(document -> !documentsToEmbed.contains(document))
                .filter(document -> needsPayloadUpdate(document, states.get(document.id()))).toList();
        if (!payloadUpdates.isEmpty()) {
            try {
                payloadUpdater.overwritePayloads(source, payloadUpdates);
                embeddingStateRepository.upsertAll(payloadUpdates.stream()
                        .map(document -> toEmbeddingState(document, fingerprint)).toList());
                counters.payloadUpdates += payloadUpdates.size();
            } catch (RuntimeException exception) {
                counters.failedDocuments += payloadUpdates.size();
                throw exception;
            }
        }

        Map<String, KnowledgeDocument> sparseDocuments = sparseIndex.documentsByIds(source, ids);
        List<KnowledgeDocument> sparseUpdates = documents.stream()
                .filter(document -> needsSparseUpdate(document, sparseDocuments.get(document.id()))).toList();
        if (!sparseUpdates.isEmpty()) {
            try {
                sparseIndex.upsertAll(sparseUpdates);
                counters.sparseUpdates += sparseUpdates.size();
            } catch (RuntimeException exception) {
                counters.failedDocuments += sparseUpdates.size();
                throw exception;
            }
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
                document.attributes().get("payloadHash"), sourceUpdatedAt(document));
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

    private int deleteIds(KnowledgeSource source, Set<String> ids, VectorStore vectorStore) {
        if (ids.isEmpty()) return 0;
        List<String> allIds = new ArrayList<>(ids);
        int batchSize = batchSize();
        for (int start = 0; start < allIds.size(); start += batchSize) {
            List<String> batch = allIds.subList(start, Math.min(start + batchSize, allIds.size()));
            vectorStore.delete(batch);
            sparseIndex.deleteAll(source, batch);
            embeddingStateRepository.deleteByDocumentIds(batch);
        }
        return allIds.size();
    }

    private List<Document> toSpringDocuments(List<KnowledgeDocument> documents) {
        return documents.stream().map(document -> new Document(
                document.id(), document.embeddingText(), document.retrievalMetadata())).toList();
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
        return hashMap(document.retrievalMetadata());
    }

    private String sparseCompatibilityHash(KnowledgeDocument document) {
        return sha256(document.id() + "\u0000" + document.embeddingText() + "\u0000" + document.displayContent()
                + "\u0000" + hashMap(document.retrievalMetadata()));
    }

    private String hashMap(Map<String, Object> values) {
        String value = values.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "\u0000" + String.valueOf(entry.getValue()))
                .collect(java.util.stream.Collectors.joining("\u0001"));
        return sha256(value);
    }

    private IndexRunReport run(IndexOperation operation, Consumer<IndexRunContext> action) {
        Instant startedAt = clock.instant();
        IndexRunContext context = new IndexRunContext(UUID.randomUUID().toString(), operation, startedAt,
                indexFingerprint().value());
        try {
            action.accept(context);
            IndexRunReport report = context.report(IndexRunReport.Status.SUCCEEDED, "", clock.instant());
            indexMetrics.recordRun(report);
            return report;
        } catch (RuntimeException exception) {
            context.fail();
            IndexRunReport report = context.report(IndexRunReport.Status.FAILED,
                    "索引操作失败: " + exception.getClass().getSimpleName(), clock.instant());
            indexMetrics.recordRun(report);
            throw exception;
        }
    }

    private Instant sourceUpdatedAt(KnowledgeDocument document) {
        String value = document.attributes().get("sourceUpdatedAt");
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private final class IndexRunContext {
        private final String runId;
        private final IndexOperation operation;
        private final Instant startedAt;
        private final String fingerprint;
        private final Map<KnowledgeSource, SourceRunCounters> counters = new LinkedHashMap<>();
        private final Map<KnowledgeSource, SourceIndexRunReport> completed = new LinkedHashMap<>();
        private KnowledgeSource activeSource;

        private IndexRunContext(String runId, IndexOperation operation, Instant startedAt, String fingerprint) {
            this.runId = runId;
            this.operation = operation;
            this.startedAt = startedAt;
            this.fingerprint = fingerprint;
        }

        private SourceRunCounters begin(KnowledgeSource source) {
            activeSource = source;
            SourceRunCounters sourceCounters = counters.computeIfAbsent(source, SourceRunCounters::new);
            indexRunStatusStore.markRunning(source, runId, operation, startedAt, fingerprint);
            return sourceCounters;
        }

        private void complete(KnowledgeSource source) {
            SourceIndexRunReport report = counters.get(source).toReport();
            completed.put(source, report);
            indexRunStatusStore.markSucceeded(report, runId, operation, startedAt, clock.instant(), fingerprint);
            activeSource = null;
        }

        private void fail() {
            if (activeSource == null) return;
            SourceRunCounters sourceCounters = counters.get(activeSource);
            SourceIndexRunReport report = sourceCounters.toReport();
            completed.put(activeSource, report);
            indexRunStatusStore.markFailed(activeSource, runId, operation, startedAt, clock.instant(), fingerprint,
                    sourceCounters.failedDocuments, "索引操作失败");
            activeSource = null;
        }

        private IndexRunReport report(IndexRunReport.Status status, String failureReason, Instant completedAt) {
            List<SourceIndexRunReport> sourceReports = List.copyOf(completed.values());
            int plantDocuments = documentsSeen(KnowledgeSource.PLANT);
            int communityDocuments = documentsSeen(KnowledgeSource.COMMUNITY);
            int diseaseDocuments = documentsSeen(KnowledgeSource.DISEASE);
            return new IndexRunReport(runId, operation, startedAt, completedAt, status,
                    plantDocuments, communityDocuments, diseaseDocuments, total(value -> value.documentsDeleted),
                    total(value -> value.documentsSeen), total(value -> value.documentsUnchanged),
                    total(value -> value.documentsEmbedded), total(value -> value.payloadUpdates),
                    total(value -> value.sparseUpdates), total(value -> value.fragmentsCreated),
                    total(value -> value.logicalEvidencesCreated), total(value -> value.failedDocuments),
                    reembedReasons(), sourceReports, failureReason);
        }

        private int documentsSeen(KnowledgeSource source) {
            SourceRunCounters sourceCounters = counters.get(source);
            return sourceCounters == null ? 0 : sourceCounters.documentsSeen;
        }

        private int total(java.util.function.ToIntFunction<SourceRunCounters> value) {
            return counters.values().stream().mapToInt(value).sum();
        }

        private Map<String, Integer> reembedReasons() {
            Map<String, Integer> result = new LinkedHashMap<>();
            counters.values().forEach(source -> source.reembedReasons.forEach(
                    (reason, count) -> result.merge(reason, count, Integer::sum)));
            return Map.copyOf(result);
        }
    }

    private static final class SourceRunCounters {
        private final KnowledgeSource source;
        private final Map<String, Integer> reembedReasons = new LinkedHashMap<>();
        private final Set<String> createdLogicalEvidenceIds = new HashSet<>();
        private int documentsSeen;
        private int documentsUnchanged;
        private int documentsEmbedded;
        private int payloadUpdates;
        private int sparseUpdates;
        private int documentsDeleted;
        private int fragmentsCreated;
        private int logicalEvidencesCreated;
        private int failedDocuments;

        private SourceRunCounters(KnowledgeSource source) {
            this.source = source;
        }

        private void recordNonEmbeddingDecision(KnowledgeDocument document,
                                                EmbeddingStateRepository.EmbeddingState state,
                                                IndexFingerprint fingerprint) {
            boolean needsEmbedding = state == null
                    || !document.attributes().get("contentHash").equals(state.contentHash())
                    || !fingerprint.value().equals(state.indexFingerprint());
            boolean needsPayload = state == null
                    || !document.attributes().get("payloadHash").equals(state.payloadHash());
            if (!needsEmbedding && !needsPayload) documentsUnchanged++;
        }

        private void recordEmbedded(KnowledgeDocument document, EmbeddingStateRepository.EmbeddingState state,
                                    IndexFingerprint fingerprint) {
            documentsEmbedded++;
            String reason = state == null ? "new_document"
                    : !document.attributes().get("contentHash").equals(state.contentHash()) ? "content_changed"
                    : !fingerprint.value().equals(state.indexFingerprint()) ? "fingerprint_changed" : "unknown";
            reembedReasons.merge(reason, 1, Integer::sum);
            if (state == null) {
                fragmentsCreated++;
                String logicalEvidenceId = document.attributes().get("logicalEvidenceId");
                if (logicalEvidenceId != null && !logicalEvidenceId.isBlank()
                        && createdLogicalEvidenceIds.add(logicalEvidenceId)) {
                    logicalEvidencesCreated++;
                }
            }
        }

        private SourceIndexRunReport toReport() {
            return new SourceIndexRunReport(source, documentsSeen, documentsUnchanged, documentsEmbedded,
                    payloadUpdates, sparseUpdates, documentsDeleted, fragmentsCreated, logicalEvidencesCreated,
                    failedDocuments, reembedReasons);
        }
    }
}
