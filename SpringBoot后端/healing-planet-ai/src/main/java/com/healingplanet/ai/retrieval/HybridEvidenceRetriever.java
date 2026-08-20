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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class HybridEvidenceRetriever implements EvidenceRetriever {
    private static final int MIXED_SOURCE_COMMUNITY_LIMIT = 2;

    private final VectorStore plantStore;
    private final VectorStore communityStore;
    private final SparseIndexService sparseIndex;
    private final KnowledgeDocumentMapper documentMapper;
    private final Reranker reranker;
    private final SourceAwareRanker ranker;
    private final PlantEntityResolver entityResolver;
    private final RetrievalMetrics metrics;
    private final RagProperties properties;

    public HybridEvidenceRetriever(@Qualifier("plantVectorStore") VectorStore plantStore,
                                   @Qualifier("communityVectorStore") VectorStore communityStore,
                                   SparseIndexService sparseIndex, KnowledgeDocumentMapper documentMapper,
                                   Reranker reranker, SourceAwareRanker ranker, PlantEntityResolver entityResolver,
                                   RetrievalMetrics metrics, RagProperties properties) {
        this.plantStore = plantStore;
        this.communityStore = communityStore;
        this.sparseIndex = sparseIndex;
        this.documentMapper = documentMapper;
        this.reranker = reranker;
        this.ranker = ranker;
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

        List<RetrievalCandidate> fused = new ArrayList<>();
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
                        () -> selectEvidence(query, filtered, rerankScores, entity)));
        trace.selected(selection.evidence(), selection.reasons());
        metrics.recordCandidates("selected", "all", selection.evidence().size());
        return new RetrievalPayload(selection.evidence(), entity.diagnostics());
    }

    private SelectionResult selectEvidence(RagQuery query, List<RetrievalCandidate> candidates,
                                           Map<String, Double> rerankScores,
                                           PlantEntityResolver.Resolution entity) {
        int finalTopK = properties.getFinalTopK();
        List<Evidence> global = ranker.rank(query, candidates, rerankScores, finalTopK);
        CoverageResult coverage = ensureMixedSourceCoverage(query, candidates, rerankScores, global, finalTopK);
        global = coverage.evidence();
        Map<String, String> globalReasons = new HashMap<>();
        global.forEach(evidence -> globalReasons.put(evidence.id(), coverage.sourceRetainedIds().contains(evidence.id())
                ? "SOURCE_RETENTION" : "GLOBAL_RANKING"));
        if (entity.kind() != PlantEntityResolver.ResolutionKind.KNOWN
                || entity.canonicalPlantIds().size() < 2) {
            return finalizeSelection(query, global, globalReasons, finalTopK);
        }

        int perEntity = Math.max(1, finalTopK / entity.canonicalPlantIds().size());
        Map<String, Evidence> selected = new LinkedHashMap<>();
        Map<String, String> reasons = new LinkedHashMap<>();
        for (String canonicalPlantId : entity.canonicalPlantIds()) {
            List<RetrievalCandidate> entityCandidates = candidates.stream()
                    .filter(candidate -> canonicalPlantId.equals(candidate.document().canonicalPlantId()))
                    .toList();
            ranker.rank(query, entityCandidates, rerankScores, perEntity).forEach(evidence -> {
                selected.putIfAbsent(evidence.id(), evidence);
                reasons.putIfAbsent(evidence.id(), "ENTITY_QUOTA");
            });
        }
        for (Evidence evidence : global) {
            selected.putIfAbsent(evidence.id(), evidence);
            reasons.putIfAbsent(evidence.id(), globalReasons.get(evidence.id()));
        }
        return finalizeSelection(query, List.copyOf(selected.values()), reasons, finalTopK);
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

    private CoverageResult ensureMixedSourceCoverage(RagQuery query, List<RetrievalCandidate> candidates,
                                                     Map<String, Double> rerankScores, List<Evidence> global,
                                                     int finalTopK) {
        boolean includePlant = booleanContext(query, "includePlantKnowledge",
                query.intent() != QueryIntent.COMMUNITY_SEARCH);
        boolean includeCommunity = booleanContext(query, "includeCommunity", true);
        if (!includePlant || !includeCommunity) return new CoverageResult(global, Set.of());

        Map<String, Evidence> selected = new LinkedHashMap<>();
        Set<String> sourceRetainedIds = new HashSet<>();
        for (KnowledgeSource source : List.of(KnowledgeSource.PLANT, KnowledgeSource.COMMUNITY)) {
            ranker.rank(query, candidates.stream().filter(candidate -> candidate.document().source() == source).toList(),
                    rerankScores, 1).stream().findFirst().ifPresent(evidence -> {
                        selected.put(evidence.id(), evidence);
                        sourceRetainedIds.add(evidence.id());
                    });
        }
        if (selected.size() < 2) return new CoverageResult(global, Set.of());
        global.forEach(evidence -> {
            if (selected.size() < finalTopK) selected.putIfAbsent(evidence.id(), evidence);
        });
        return new CoverageResult(selected.values().stream().limit(finalTopK).toList(), sourceRetainedIds);
    }

    private SelectionResult finalizeSelection(RagQuery query, List<Evidence> evidence,
                                              Map<String, String> reasons, int finalTopK) {
        if (!isMixedSourceQuery(query)) {
            return new SelectionResult(evidence.stream().limit(finalTopK).toList(), reasons);
        }
        List<Evidence> limited = limitCommunityEvidence(evidence, finalTopK);
        Map<String, String> limitedReasons = new LinkedHashMap<>();
        limited.forEach(item -> limitedReasons.put(item.id(),
                reasons.getOrDefault(item.id(), "GLOBAL_RANKING")));
        return new SelectionResult(limited, limitedReasons);
    }

    private boolean isMixedSourceQuery(RagQuery query) {
        return booleanContext(query, "includePlantKnowledge", query.intent() != QueryIntent.COMMUNITY_SEARCH)
                && booleanContext(query, "includeCommunity", true);
    }

    private List<Evidence> limitCommunityEvidence(List<Evidence> evidence, int finalTopK) {
        int communityLimit = Math.min(MIXED_SOURCE_COMMUNITY_LIMIT, Math.max(0, finalTopK - 1));
        List<Evidence> result = new ArrayList<>();
        Set<String> communitySourceIds = new HashSet<>();
        int communityCount = 0;
        for (Evidence item : evidence) {
            if (item.type() != com.healingplanet.ai.domain.EvidenceType.COMMUNITY_POST) {
                result.add(item);
            } else if (communityCount < communityLimit) {
                String sourceKey = item.sourceId() == null || item.sourceId().isBlank()
                        ? item.id() : item.sourceId();
                if (communitySourceIds.add(sourceKey)) {
                    result.add(item);
                    communityCount++;
                }
            }
            if (result.size() >= finalTopK) break;
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
        SearchRequest.Builder requestBuilder = SearchRequest.builder().query(query)
                .topK(properties.getDenseTopK())
                .similarityThreshold(properties.getSimilarityThreshold());
        if (source == KnowledgeSource.PLANT && entity.kind() == PlantEntityResolver.ResolutionKind.KNOWN) {
            requestBuilder.filterExpression(new FilterExpressionBuilder()
                    .eq("canonicalPlantId", entity.canonicalPlantId()).build());
        }
        SearchRequest request = requestBuilder.build();
        String sourceTag = source.name().toLowerCase(java.util.Locale.ROOT);
        String scope = entity.kind() == PlantEntityResolver.ResolutionKind.KNOWN
                ? String.join(",", entity.canonicalPlantIds()) : entity.kind().name();
        List<org.springframework.ai.document.Document> documents = trace.time("dense_search", sourceTag, scope,
                () -> metrics.time("embedding", sourceTag, () -> store.similaritySearch(request)));
        List<RrfFusion.DenseHit> denseRaw = documents.stream()
                .map(document -> new RrfFusion.DenseHit(documentMapper.fromSpring(document, source),
                        document.getScore() == null ? 0d : document.getScore()))
                .toList();
        trace.dense(denseRaw, scope);
        List<RrfFusion.DenseHit> dense = metrics.time("dense_search", sourceTag,
                () -> denseRaw.stream()
                .filter(hit -> entityResolver.matches(entity, hit.document()))
                .toList());
        List<SparseIndexService.SparseHit> sparseRaw = trace.time("sparse_search", sourceTag, scope,
                () -> metrics.time("sparse_search", sourceTag,
                        () -> sparseIndex.search(source, query, properties.getSparseTopK())));
        trace.sparse(sparseRaw, scope);
        List<SparseIndexService.SparseHit> sparse = sparseRaw.stream()
                        .filter(hit -> entityResolver.matches(entity, hit.document()))
                        .toList();
        metrics.recordCandidates("dense", sourceTag, dense.size());
        metrics.recordCandidates("sparse", sourceTag, sparse.size());
        List<RetrievalCandidate> result = trace.time("rrf_fusion", sourceTag, scope,
                () -> metrics.time("rrf_fusion", sourceTag, () -> RrfFusion.fuse(dense, sparse)));
        trace.rrf(result, scope);
        metrics.recordCandidates("fused", sourceTag, result.size());
        return result;
    }

    private record RetrievalPayload(List<Evidence> evidence,
                                    com.healingplanet.ai.domain.EntityResolutionDiagnostics entityResolution) { }

    private record SelectionResult(List<Evidence> evidence, Map<String, String> reasons) { }

    private record CoverageResult(List<Evidence> evidence, Set<String> sourceRetainedIds) { }
}
