package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.domain.Evidence;
import com.healingplanet.ai.domain.KnowledgeSource;
import com.healingplanet.ai.domain.QueryIntent;
import com.healingplanet.ai.domain.RagQuery;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashSet;
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

    public HybridEvidenceRetriever(@Qualifier("plantVectorStore") VectorStore plantStore,
                                   @Qualifier("communityVectorStore") VectorStore communityStore,
                                   SparseIndexService sparseIndex, KnowledgeDocumentMapper documentMapper,
                                   Reranker reranker, SourceAwareRanker ranker, PlantEntityResolver entityResolver,
                                   EvidenceSelector evidenceSelector, RetrievalMetrics metrics, RagProperties properties) {
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
    }

    @Override
    public List<Evidence> retrieve(RagQuery query) {
        return retrieveWithDiagnostics(query).evidence();
    }

    @Override
    public RetrievalResult retrieveWithDiagnostics(RagQuery query) {
        RetrievalTraceCollector trace = new RetrievalTraceCollector(
                properties.getEval().isRetrievalTraceEnabled());
        RetrievalPayload payload = trace.time("knowledge_total", "all", "all",
                () -> metrics.time("knowledge_total", "all", () -> retrieveTimed(query, trace)));
        return new RetrievalResult(payload.evidence(), payload.entityResolution(),
                trace.build(payload.entityResolution()));
    }

    private RetrievalPayload retrieveTimed(RagQuery query, RetrievalTraceCollector trace) {
        PlantEntityResolver.Resolution entity = trace.time("entity_resolve", "all", "all",
                () -> metrics.time("entity_resolve", "all", () -> entityResolver.resolve(query)));
        if (entity.kind() == PlantEntityResolver.ResolutionKind.UNKNOWN
                || entity.kind() == PlantEntityResolver.ResolutionKind.AMBIGUOUS
                || entity.kind() == PlantEntityResolver.ResolutionKind.OUT_OF_DOMAIN) {
            metrics.recordCandidates("selected", "all", 0);
            return new RetrievalPayload(List.of(), entity.diagnostics());
        }

        List<RetrievalCandidate> fused = new java.util.ArrayList<>();
        boolean includePlant = booleanContext(query, "includePlantKnowledge", query.intent() != QueryIntent.COMMUNITY_SEARCH);
        boolean includeCommunity = booleanContext(query, "includeCommunity", true);
        if (includePlant) {
            if (entity.kind() == PlantEntityResolver.ResolutionKind.KNOWN
                    && entity.canonicalPlantIds().size() > 1) {
                for (String canonicalPlantId : entity.canonicalPlantIds()) {
                    fused.addAll(retrieveSource(query.query(), KnowledgeSource.PLANT, plantStore,
                            PlantEntityResolver.Resolution.forCanonicalPlantId(canonicalPlantId), trace));
                }
            } else {
                fused.addAll(retrieveSource(query.query(), KnowledgeSource.PLANT, plantStore, entity, trace));
            }
        }
        if (includeCommunity) {
            fused.addAll(retrieveSource(query.query(), KnowledgeSource.COMMUNITY, communityStore, entity, trace));
        }
        metrics.recordCandidates("fused", "all", fused.size());
        List<RetrievalCandidate> filtered = trace.time("knowledge_type_filter", "all", "all",
                () -> filterKnowledgeType(query, fused));
        trace.filtered(filtered);
        Map<String, Double> rerankScores = trace.time("rerank", "all", "all",
                () -> metrics.time("rerank", "all", () -> reranker.rerank(query.query(), filtered)));
        List<RetrievalCandidate> reranked = filtered.stream()
                .sorted(Comparator.comparingDouble((RetrievalCandidate candidate) -> rerankScores
                        .getOrDefault(candidate.document().id(), Double.NEGATIVE_INFINITY)).reversed())
                .toList();
        trace.rerank(filtered, reranked, rerankScores);
        SelectionResult selection = trace.time("final_rank", "all", "all",
                () -> metrics.time("final_rank", "all",
                        () -> selectEvidence(query, filtered, rerankScores, entity, trace)));
        trace.selected(selection.evidence(), selection.reasons());
        metrics.recordCandidates("selected", "all", selection.evidence().size());
        return new RetrievalPayload(selection.evidence(), entity.diagnostics());
    }

    private SelectionResult selectEvidence(RagQuery query, List<RetrievalCandidate> candidates,
                                           Map<String, Double> rerankScores,
                                           PlantEntityResolver.Resolution entity,
                                           RetrievalTraceCollector trace) {
        List<Evidence> ranked = ranker.rank(query, candidates, rerankScores);
        trace.preSelectionRanked(ranked);
        if (!properties.getEvidenceSelector().isEnabled()) {
            List<Evidence> selected = ranked.stream().limit(properties.getFinalTopK()).toList();
            Map<String, String> reasons = new LinkedHashMap<>();
            selected.forEach(evidence -> reasons.put(evidence.id(), "GLOBAL_RANKING"));
            return new SelectionResult(selected, reasons);
        }
        EvidenceSelector.Selection selection = evidenceSelector.select(query, ranked, properties.getFinalTopK(),
                entity.kind() == PlantEntityResolver.ResolutionKind.KNOWN
                        ? entity.canonicalPlantIds() : List.of());
        return new SelectionResult(selection.evidence(), selection.reasons());
    }

    private List<RetrievalCandidate> filterKnowledgeType(RagQuery query, List<RetrievalCandidate> candidates) {
        Set<String> types = requiredKnowledgeTypes(query);
        if (types.isEmpty()) return candidates;
        return candidates.stream()
                .filter(candidate -> candidate.document().source() != KnowledgeSource.PLANT
                        || candidate.document().knowledgeType() != null
                        && types.contains(candidate.document().knowledgeType().toUpperCase(java.util.Locale.ROOT)))
                .toList();
    }

    private Set<String> requiredKnowledgeTypes(RagQuery query) {
        Object required = query.context().get("requiredKnowledgeType");
        if (required instanceof String type && !type.isBlank()) {
            return Set.of(type.toUpperCase(java.util.Locale.ROOT));
        }
        Object multi = query.context().get("requiredKnowledgeTypes");
        if (!(multi instanceof Iterable<?> values)) return Set.of();
        Set<String> result = new HashSet<>();
        for (Object value : values) {
            if (value instanceof String type && !type.isBlank()) {
                result.add(type.toUpperCase(java.util.Locale.ROOT));
            }
        }
        return result;
    }

    private boolean booleanContext(RagQuery query, String key, boolean defaultValue) {
        Object value = query.context().get(key);
        return value instanceof Boolean bool ? bool : defaultValue;
    }

    private List<RetrievalCandidate> retrieveSource(String query, KnowledgeSource source, VectorStore store,
                                                    PlantEntityResolver.Resolution entity,
                                                    RetrievalTraceCollector trace) {
        String sourceTag = source.name().toLowerCase(java.util.Locale.ROOT);
        String scope = entity.kind() == PlantEntityResolver.ResolutionKind.KNOWN
                ? String.join(",", entity.canonicalPlantIds()) : entity.kind().name();
        List<org.springframework.ai.document.Document> documents = properties.getRetrievalMode().usesDense()
                ? denseSearch(query, source, store, entity, trace, sourceTag, scope) : List.of();
        List<RrfFusion.DenseHit> denseRaw = documents.stream()
                .map(document -> new RrfFusion.DenseHit(documentMapper.fromSpring(document, source),
                        document.getScore() == null ? 0d : document.getScore()))
                .toList();
        trace.dense(denseRaw, scope);
        List<RrfFusion.DenseHit> dense = metrics.time("dense_search", sourceTag,
                () -> denseRaw.stream()
                .filter(hit -> entityResolver.matches(entity, hit.document()))
                .toList());
        List<SparseIndexService.SparseHit> sparseRaw = properties.getRetrievalMode().usesSparse()
                ? trace.time("sparse_search", sourceTag, scope,
                    () -> metrics.time("sparse_search", sourceTag,
                            () -> sparseIndex.search(source, query, properties.getSparseTopK())))
                : List.of();
        trace.sparse(sparseRaw, scope);
        List<SparseIndexService.SparseHit> sparse = sparseRaw.stream()
                        .filter(hit -> entityResolver.matches(entity, hit.document()))
                        .toList();
        metrics.recordCandidates("dense", sourceTag, dense.size());
        metrics.recordCandidates("sparse", sourceTag, sparse.size());
        List<RetrievalCandidate> result = trace.time("rrf_fusion", sourceTag, scope,
                () -> metrics.time("rrf_fusion", sourceTag,
                        () -> RrfFusion.fuse(dense, sparse, properties.getRrfK())));
        trace.rrf(result, scope);
        metrics.recordCandidates("fused", sourceTag, result.size());
        return result;
    }

    private List<org.springframework.ai.document.Document> denseSearch(
            String query, KnowledgeSource source, VectorStore store,
            PlantEntityResolver.Resolution entity, RetrievalTraceCollector trace,
            String sourceTag, String scope) {
        SearchRequest.Builder requestBuilder = SearchRequest.builder().query(query)
                .topK(properties.getDenseTopK())
                .similarityThreshold(properties.getSimilarityThreshold());
        if (source == KnowledgeSource.PLANT && entity.kind() == PlantEntityResolver.ResolutionKind.KNOWN) {
            requestBuilder.filterExpression(new FilterExpressionBuilder()
                    .eq("canonicalPlantId", entity.canonicalPlantId()).build());
        }
        SearchRequest request = requestBuilder.build();
        return trace.time("dense_search", sourceTag, scope,
                () -> metrics.time("embedding", sourceTag, () -> store.similaritySearch(request)));
    }

    private record RetrievalPayload(List<Evidence> evidence,
                                    com.healingplanet.ai.domain.EntityResolutionDiagnostics entityResolution) { }

    private record SelectionResult(List<Evidence> evidence, Map<String, String> reasons) { }

}
