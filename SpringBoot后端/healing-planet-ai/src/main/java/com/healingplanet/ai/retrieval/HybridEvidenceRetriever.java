package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.config.RagRuntimeConfig;
import com.healingplanet.ai.config.RagRuntimeConfigProvider;
import com.healingplanet.ai.config.RagRuntimeSnapshot;
import com.healingplanet.ai.domain.Evidence;
import com.healingplanet.ai.domain.KnowledgeSource;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class HybridEvidenceRetriever implements EvidenceRetriever {
    private final VectorStore plantStore;
    private final VectorStore communityStore;
    private final SparseIndexService sparseIndex;
    private final KnowledgeDocumentMapper documentMapper;
    private final Reranker reranker;
    private final SourceAwareRanker ranker;
    private final EvidenceSelector evidenceSelector;
    private final PlantEntityResolver entityResolver;
    private final RetrievalMetrics metrics;
    private final RagProperties properties;
    private final RagRuntimeConfigProvider runtimeConfigProvider;

    @Autowired
    public HybridEvidenceRetriever(@Qualifier("plantVectorStore") VectorStore plantStore,
                                   @Qualifier("communityVectorStore") VectorStore communityStore,
                                   SparseIndexService sparseIndex, KnowledgeDocumentMapper documentMapper,
                                   Reranker reranker, SourceAwareRanker ranker, PlantEntityResolver entityResolver,
                                   EvidenceSelector evidenceSelector, RetrievalMetrics metrics, RagProperties properties,
                                   RagRuntimeConfigProvider runtimeConfigProvider) {
        this.plantStore = plantStore;
        this.communityStore = communityStore;
        this.sparseIndex = sparseIndex;
        this.documentMapper = documentMapper;
        this.reranker = reranker;
        this.ranker = ranker;
        this.evidenceSelector = evidenceSelector;
        this.entityResolver = entityResolver;
        this.metrics = metrics;
        this.properties = properties;
        this.runtimeConfigProvider = runtimeConfigProvider;
    }

    HybridEvidenceRetriever(VectorStore plantStore, VectorStore communityStore,
                            SparseIndexService sparseIndex, KnowledgeDocumentMapper documentMapper,
                            Reranker reranker, SourceAwareRanker ranker, PlantEntityResolver entityResolver,
                            EvidenceSelector evidenceSelector, RetrievalMetrics metrics, RagProperties properties) {
        this(plantStore, communityStore, sparseIndex, documentMapper, reranker, ranker, entityResolver,
                evidenceSelector, metrics, properties, new RagRuntimeConfigProvider(properties));
    }

    @Override
    public List<Evidence> retrieve(RetrievalRequest request) {
        return retrieveWithDiagnostics(request).evidence();
    }

    @Override
    public RetrievalResult retrieveWithDiagnostics(RetrievalRequest request) {
        return retrieveWithDiagnostics(request, runtimeConfigProvider.runtimeSnapshot());
    }

    public RetrievalResult retrieveWithDiagnostics(RetrievalRequest request, RagRuntimeConfig config) {
        return retrieveWithDiagnostics(request, new RagRuntimeSnapshot(config, null));
    }

    public RetrievalResult retrieveWithDiagnostics(RetrievalRequest request, RagRuntimeSnapshot runtimeSnapshot) {
        RagRuntimeConfig config = runtimeSnapshot.config();
        RetrievalTraceCollector trace = new RetrievalTraceCollector(
                properties.getEval().isRetrievalTraceEnabled());
        RetrievalPayload payload = trace.time("knowledge_total", "all", "all",
                () -> metrics.time("knowledge_total", "all", () -> retrieveTimed(request, trace, runtimeSnapshot)));
        return new RetrievalResult(payload.evidence(), payload.entityResolution(),
                trace.build(payload.entityResolution()));
    }

    private RetrievalPayload retrieveTimed(RetrievalRequest request, RetrievalTraceCollector trace,
                                           RagRuntimeSnapshot runtimeSnapshot) {
        RagRuntimeConfig config = runtimeSnapshot.config();
        PlantEntityResolver.Resolution entity = trace.time("entity_resolve", "all", "all",
                () -> metrics.time("entity_resolve", "all", () -> request.entityResolution() == null
                        ? entityResolver.resolve(request) : request.entityResolution()));
        List<RetrievalCandidate> fused = new java.util.ArrayList<>();
        boolean identityBlocked = entity.kind() == PlantEntityResolver.ResolutionKind.UNKNOWN
                || entity.kind() == PlantEntityResolver.ResolutionKind.AMBIGUOUS
                || entity.kind() == PlantEntityResolver.ResolutionKind.CONFLICT;
        boolean includePlant = request.plan().searchKnowledge() && !identityBlocked;
        boolean includeCommunity = request.plan().searchCommunity() && !identityBlocked
                && entity.kind() != PlantEntityResolver.ResolutionKind.PARTIAL;
        if (includePlant) {
            fused.addAll(retrieveSource(request.searchQuery(), KnowledgeSource.PLANT, plantStore, entity, trace, config));
        }
        if (includeCommunity) {
            fused.addAll(retrieveSource(request.searchQuery(), KnowledgeSource.COMMUNITY, communityStore, entity, trace, config));
        }
        metrics.recordCandidates("fused", "all", fused.size());
        // Topic hints retain all candidates and are consumed only by EvidenceSelector coverage.
        List<RetrievalCandidate> candidates = List.copyOf(fused);
        trace.filtered(candidates);
        Map<String, Double> rerankScores = trace.time("rerank", "all", "all",
                () -> metrics.time("rerank", "all", () -> rerank(request.searchQuery(), candidates, runtimeSnapshot)));
        List<RetrievalCandidate> reranked = candidates.stream()
                .sorted(Comparator.comparingDouble((RetrievalCandidate candidate) -> rerankScores
                        .getOrDefault(candidate.document().id(), Double.NEGATIVE_INFINITY)).reversed())
                .toList();
        trace.rerank(candidates, reranked, rerankScores);
        SelectionResult selection = trace.time("final_rank", "all", "all",
                () -> metrics.time("final_rank", "all",
                        () -> selectEvidence(request, candidates, rerankScores, entity, trace, runtimeSnapshot.config())));
        trace.selected(selection.evidence(), selection.reasons());
        metrics.recordCandidates("selected", "all", selection.evidence().size());
        return new RetrievalPayload(selection.evidence(), entity.diagnostics());
    }

    private SelectionResult selectEvidence(RetrievalRequest request, List<RetrievalCandidate> candidates,
                                           Map<String, Double> rerankScores,
                                           PlantEntityResolver.Resolution entity,
                                           RetrievalTraceCollector trace, RagRuntimeConfig config) {
        List<Evidence> ranked = ranker.rank(request.query(), candidates, rerankScores, config);
        trace.preSelectionRanked(ranked);
        if (!config.evidenceSelectorEnabled()) {
            List<Evidence> selected = ranked.stream().limit(config.finalTopK()).toList();
            Map<String, String> reasons = new LinkedHashMap<>();
            selected.forEach(evidence -> reasons.put(evidence.id(), "GLOBAL_RANKING"));
            return new SelectionResult(selected, reasons);
        }
        EvidenceSelector.Selection selection = evidenceSelector.select(request, ranked, config.finalTopK(),
                entity.hasResolvedEntities() ? entity.canonicalPlantIds() : List.of(), config);
        return new SelectionResult(selection.evidence(), selection.reasons());
    }

    private List<RetrievalCandidate> retrieveSource(String query, KnowledgeSource source, VectorStore store,
                                                    PlantEntityResolver.Resolution entity,
                                                    RetrievalTraceCollector trace, RagRuntimeConfig config) {
        String sourceTag = source.name().toLowerCase(java.util.Locale.ROOT);
        String scope = entity.hasResolvedEntities()
                ? String.join(",", entity.canonicalPlantIds()) : entity.kind().name();
        List<org.springframework.ai.document.Document> documents = config.retrievalMode().usesDense()
                ? denseSearch(query, source, store, entity, trace, sourceTag, scope, config) : List.of();
        List<RrfFusion.DenseHit> denseRaw = documents.stream()
                .map(document -> new RrfFusion.DenseHit(documentMapper.fromSpring(document, source),
                        document.getScore() == null ? 0d : document.getScore()))
                .toList();
        trace.dense(denseRaw, scope);
        List<RrfFusion.DenseHit> dense = denseRaw;
        List<SparseIndexService.SparseHit> sparseRaw = config.retrievalMode().usesSparse()
                ? trace.time("sparse_search", sourceTag, scope,
                    () -> metrics.time("sparse_search", sourceTag,
                            () -> sparseIndex.search(source, query, config.sparseTopK(),
                                    source == KnowledgeSource.PLANT && entity.scope().filtersPlantKnowledge()
                                            ? entity.canonicalPlantIds() : List.of())))
                : List.of();
        trace.sparse(sparseRaw, scope);
        List<SparseIndexService.SparseHit> sparse = sparseRaw;
        metrics.recordCandidates("dense", sourceTag, dense.size());
        metrics.recordCandidates("sparse", sourceTag, sparse.size());
        List<RetrievalCandidate> result = trace.time("rrf_fusion", sourceTag, scope,
                () -> metrics.time("rrf_fusion", sourceTag,
                        () -> RrfFusion.fuse(dense, sparse, config.rrfK())));
        trace.rrf(result, scope);
        metrics.recordCandidates("fused", sourceTag, result.size());
        return result;
    }

    private List<org.springframework.ai.document.Document> denseSearch(
            String query, KnowledgeSource source, VectorStore store,
            PlantEntityResolver.Resolution entity, RetrievalTraceCollector trace,
            String sourceTag, String scope, RagRuntimeConfig config) {
        SearchRequest.Builder requestBuilder = SearchRequest.builder().query(query)
                .topK(config.denseTopK())
                .similarityThreshold(config.similarityThreshold());
        if (source == KnowledgeSource.PLANT && entity.scope().filtersPlantKnowledge()) {
            FilterExpressionBuilder filters = new FilterExpressionBuilder();
            requestBuilder.filterExpression(entity.canonicalPlantIds().size() == 1
                    ? filters.eq("canonicalPlantId", entity.canonicalPlantId()).build()
                    : filters.in("canonicalPlantId", entity.canonicalPlantIds().toArray()).build());
        }
        SearchRequest request = requestBuilder.build();
        return trace.time("dense_search", sourceTag, scope,
                () -> metrics.time("embedding", sourceTag, () -> store.similaritySearch(request)));
    }

    private Map<String, Double> rerank(String query, List<RetrievalCandidate> candidates, RagRuntimeSnapshot runtimeSnapshot) {
        if (reranker instanceof SnapshotReranker snapshotReranker) {
            return snapshotReranker.rerank(query, candidates, runtimeSnapshot);
        }
        return reranker.rerank(query, candidates);
    }

    private record RetrievalPayload(List<Evidence> evidence,
                                    com.healingplanet.ai.domain.EntityResolutionDiagnostics entityResolution) { }

    private record SelectionResult(List<Evidence> evidence, Map<String, String> reasons) { }

}
