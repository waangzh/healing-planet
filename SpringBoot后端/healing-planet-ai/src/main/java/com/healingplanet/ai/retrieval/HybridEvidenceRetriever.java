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
import java.util.LinkedHashSet;
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
    private final CoverageInspector coverageInspector;
    private final AdaptiveRecallPolicy adaptiveRecallPolicy;
    private final RecallQualificationPolicy recallQualificationPolicy;
    private final PlantCatalogIndex plantCatalogIndex;
    private final CommunityRankingFeatureHydrator communityRankingFeatureHydrator;
    private final LogicalEvidenceCandidateMerger candidateMerger = new LogicalEvidenceCandidateMerger();

    @Autowired
    public HybridEvidenceRetriever(@Qualifier("plantVectorStore") VectorStore plantStore,
                                   @Qualifier("communityVectorStore") VectorStore communityStore,
                                   SparseIndexService sparseIndex, KnowledgeDocumentMapper documentMapper,
                                   Reranker reranker, SourceAwareRanker ranker, PlantEntityResolver entityResolver,
                                   EvidenceSelector evidenceSelector, RetrievalMetrics metrics, RagProperties properties,
                                   RagRuntimeConfigProvider runtimeConfigProvider, CoverageInspector coverageInspector,
                                   AdaptiveRecallPolicy adaptiveRecallPolicy,
                                   RecallQualificationPolicy recallQualificationPolicy,
                                   PlantCatalogIndex plantCatalogIndex,
                                   CommunityRankingFeatureHydrator communityRankingFeatureHydrator) {
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
        this.coverageInspector = coverageInspector;
        this.adaptiveRecallPolicy = adaptiveRecallPolicy;
        this.recallQualificationPolicy = recallQualificationPolicy;
        this.plantCatalogIndex = plantCatalogIndex;
        this.communityRankingFeatureHydrator = communityRankingFeatureHydrator;
    }

    HybridEvidenceRetriever(VectorStore plantStore, VectorStore communityStore,
                            SparseIndexService sparseIndex, KnowledgeDocumentMapper documentMapper,
                            Reranker reranker, SourceAwareRanker ranker, PlantEntityResolver entityResolver,
                            EvidenceSelector evidenceSelector, RetrievalMetrics metrics, RagProperties properties) {
        this(plantStore, communityStore, sparseIndex, documentMapper, reranker, ranker, entityResolver,
                evidenceSelector, metrics, properties, PlantCatalogIndex.empty());
    }

    HybridEvidenceRetriever(VectorStore plantStore, VectorStore communityStore,
                            SparseIndexService sparseIndex, KnowledgeDocumentMapper documentMapper,
                            Reranker reranker, SourceAwareRanker ranker, PlantEntityResolver entityResolver,
                            EvidenceSelector evidenceSelector, RetrievalMetrics metrics, RagProperties properties,
                            PlantCatalogIndex plantCatalogIndex) {
        this(plantStore, communityStore, sparseIndex, documentMapper, reranker, ranker, entityResolver,
                evidenceSelector, metrics, properties, new RagRuntimeConfigProvider(properties),
                new CoverageInspector(), new AdaptiveRecallPolicy(), new RecallQualificationPolicy(),
                plantCatalogIndex, CommunityRankingFeatureHydrator.noOp());
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

    @Override
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
        boolean identityBlocked = entity.kind() == PlantEntityResolver.ResolutionKind.UNKNOWN
                || entity.kind() == PlantEntityResolver.ResolutionKind.AMBIGUOUS
                || entity.kind() == PlantEntityResolver.ResolutionKind.CONFLICT;
        boolean includePlant = request.plan().searchKnowledge() && !identityBlocked;
        boolean includeCommunity = request.plan().searchCommunity() && !identityBlocked
                && entity.kind() != PlantEntityResolver.ResolutionKind.PARTIAL;
        RecallState recall = recallWithCoverage(request, entity, includePlant, includeCommunity,
                trace, config);
        List<LogicalEvidenceCandidate> candidates = recall.candidates();
        RecallBudget budget = recall.budget();
        Map<RecallRoute, List<LogicalEvidenceCandidate>> recalled = recall.recalled();
        RerankResult rerank = rerankCandidates(request, request.searchQuery(), candidates, trace, runtimeSnapshot);
        QualifiedRecallCoverage qualification = recallQualificationPolicy.inspect(request, rerank.candidates(),
                rerank.scores(), config);
        while (!qualification.sufficient()) {
            RecallBudget expanded = adaptiveRecallPolicy.next(qualification, budget, config);
            if (!adaptiveRecallPolicy.expanded(budget, expanded)) break;
            Set<KnowledgeSource> sources = expandedSources(budget, expanded);
            trace.time("corrective_recall", sourceTag(sources), "all", () -> {
                recall(request, entity, includePlant, includeCommunity, expanded, sources, recalled, trace, config);
                return null;
            });
            budget = expanded;
            candidates = merge(recalled);
            rerank = rerankCandidates(request, request.searchQuery(), candidates, trace, runtimeSnapshot);
            qualification = recallQualificationPolicy.inspect(request, rerank.candidates(), rerank.scores(), config);
        }
        metrics.recordCandidates("fused", "all", candidates.size());
        trace.filtered(candidates);
        RerankResult finalRerank = new RerankResult(communityRankingFeatureHydrator.hydrate(rerank.candidates()),
                rerank.scores());
        trace.rerank(candidates, finalRerank.candidates(), finalRerank.scores());
        SelectionResult selection = trace.time("final_rank", "all", "all",
                () -> metrics.time("final_rank", "all",
                        () -> selectEvidence(request, finalRerank.candidates(), finalRerank.scores(), entity, trace,
                                runtimeSnapshot.config())));
        trace.selected(selection.evidence(), selection.reasons());
        metrics.recordCandidates("selected", "all", selection.evidence().size());
        return new RetrievalPayload(selection.evidence(), entity.diagnostics());
    }

    private RecallState recallWithCoverage(RetrievalRequest request, PlantEntityResolver.Resolution entity,
                                           boolean includePlant, boolean includeCommunity,
                                           RetrievalTraceCollector trace, RagRuntimeConfig config) {
        Map<RecallRoute, List<LogicalEvidenceCandidate>> recalled = new LinkedHashMap<>();
        RecallBudget budget = adaptiveRecallPolicy.initial(config);
        recall(request, entity, includePlant, includeCommunity, budget,
                activeSources(includePlant, includeCommunity), recalled, trace, config);
        List<LogicalEvidenceCandidate> candidates = merge(recalled);
        RecallCoverage coverage = coverageInspector.inspect(request, candidates, config);
        while (!coverage.sufficient()) {
            RecallBudget expanded = adaptiveRecallPolicy.next(request, coverage, budget, config);
            if (!adaptiveRecallPolicy.expanded(budget, expanded)) break;
            Set<KnowledgeSource> sources = expandedSources(budget, expanded);
            trace.time("adaptive_recall", sourceTag(sources), "all", () -> {
                recall(request, entity, includePlant, includeCommunity, expanded, sources, recalled, trace, config);
                return null;
            });
            budget = expanded;
            candidates = merge(recalled);
            coverage = coverageInspector.inspect(request, candidates, config);
        }
        return new RecallState(candidates, budget, recalled);
    }

    private void recall(RetrievalRequest request, PlantEntityResolver.Resolution entity, boolean includePlant,
                        boolean includeCommunity, RecallBudget budget, Set<KnowledgeSource> sources,
                        Map<RecallRoute, List<LogicalEvidenceCandidate>> recalled, RetrievalTraceCollector trace,
                        RagRuntimeConfig config) {
        for (RetrievalQueryGroup group : request.plan().queryGroups()) {
            if (includePlant && sources.contains(KnowledgeSource.PLANT)
                    && group.sourceScope().includes(KnowledgeSource.PLANT)) {
                recalled.put(new RecallRoute(KnowledgeSource.PLANT, group.id()), retrieveSource(group,
                        KnowledgeSource.PLANT, plantStore, entity, budget, trace, config));
            }
            if (includeCommunity && sources.contains(KnowledgeSource.COMMUNITY)
                    && group.sourceScope().includes(KnowledgeSource.COMMUNITY)) {
                recalled.put(new RecallRoute(KnowledgeSource.COMMUNITY, group.id()), retrieveSource(group,
                        KnowledgeSource.COMMUNITY, communityStore, entity, budget, trace, config));
            }
        }
    }

    private List<LogicalEvidenceCandidate> merge(Map<RecallRoute, List<LogicalEvidenceCandidate>> recalled) {
        List<LogicalEvidenceCandidateMerger.GroupCandidate> candidates = new java.util.ArrayList<>();
        recalled.forEach((route, values) -> values.forEach(candidate -> candidates.add(
                new LogicalEvidenceCandidateMerger.GroupCandidate(route.groupId(), candidate))));
        return candidateMerger.merge(candidates);
    }

    private Set<KnowledgeSource> activeSources(boolean includePlant, boolean includeCommunity) {
        Set<KnowledgeSource> sources = new LinkedHashSet<>();
        if (includePlant) sources.add(KnowledgeSource.PLANT);
        if (includeCommunity) sources.add(KnowledgeSource.COMMUNITY);
        return sources;
    }

    private Set<KnowledgeSource> expandedSources(RecallBudget before, RecallBudget after) {
        Set<KnowledgeSource> sources = new LinkedHashSet<>();
        if (before.plantDenseTopK() != after.plantDenseTopK()
                || before.plantSparseTopK() != after.plantSparseTopK()) sources.add(KnowledgeSource.PLANT);
        if (before.communityDenseTopK() != after.communityDenseTopK()
                || before.communitySparseTopK() != after.communitySparseTopK()) sources.add(KnowledgeSource.COMMUNITY);
        return sources;
    }

    private String sourceTag(Set<KnowledgeSource> sources) {
        return sources.stream().map(source -> source.name().toLowerCase(java.util.Locale.ROOT)).sorted()
                .collect(java.util.stream.Collectors.joining(","));
    }

    private SelectionResult selectEvidence(RetrievalRequest request, List<LogicalEvidenceCandidate> candidates,
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

    private List<LogicalEvidenceCandidate> retrieveSource(RetrievalQueryGroup group, KnowledgeSource source,
                                                          VectorStore store, PlantEntityResolver.Resolution entity,
                                                          RecallBudget budget,
                                                          RetrievalTraceCollector trace, RagRuntimeConfig config) {
        String sourceTag = source.name().toLowerCase(java.util.Locale.ROOT);
        List<String> canonicalPlantIds = canonicalPlantIds(source, group, entity);
        String query = sourceQuery(source, group);
        String scope = group.id() + ":" + (canonicalPlantIds.isEmpty() ? entity.kind().name()
                : String.join(",", canonicalPlantIds));
        List<org.springframework.ai.document.Document> documents = config.retrievalMode().usesDense()
                ? denseSearch(query, source, store, canonicalPlantIds, budget.denseTopK(source), trace,
                sourceTag, scope, config) : List.of();
        List<RrfFusion.DenseHit> denseRaw = documents.stream()
                .map(document -> new RrfFusion.DenseHit(documentMapper.fromSpring(document, source),
                        document.getScore() == null ? 0d : document.getScore()))
                .toList();
        trace.dense(denseRaw, scope);
        List<RrfFusion.DenseHit> dense = denseRaw;
        List<SparseIndexService.SparseHit> sparseRaw = config.retrievalMode().usesSparse()
                ? trace.time("sparse_search", sourceTag, scope,
                    () -> metrics.time("sparse_search", sourceTag,
                            () -> sparseIndex.search(source, query, budget.sparseTopK(source),
                                    canonicalPlantIds)))
                : List.of();
        trace.sparse(sparseRaw, scope);
        List<SparseIndexService.SparseHit> sparse = sparseRaw;
        metrics.recordCandidates("dense", sourceTag, dense.size());
        metrics.recordCandidates("sparse", sourceTag, sparse.size());
        List<LogicalEvidenceCandidate> result = trace.time("rrf_fusion", sourceTag, scope,
                () -> metrics.time("rrf_fusion", sourceTag,
                        () -> RrfFusion.fuse(dense, sparse, config.rrfK())));
        trace.rrf(result, scope);
        metrics.recordCandidates("fused", sourceTag, result.size());
        return result;
    }

    private String sourceQuery(KnowledgeSource source, RetrievalQueryGroup group) {
        if (source != KnowledgeSource.COMMUNITY || group.canonicalPlantIds().isEmpty()) return group.query();
        String names = group.canonicalPlantIds().stream().map(plantCatalogIndex::canonicalPlantName)
                .filter(name -> name != null && !name.isBlank()).distinct()
                .collect(java.util.stream.Collectors.joining("、"));
        return names.isBlank() ? group.query() : group.query() + "\n目标植物：" + names;
    }

    private List<String> canonicalPlantIds(KnowledgeSource source, RetrievalQueryGroup group,
                                            PlantEntityResolver.Resolution entity) {
        if (source != KnowledgeSource.PLANT || !entity.scope().filtersPlantKnowledge()) return List.of();
        return group.canonicalPlantIds().isEmpty() ? entity.canonicalPlantIds() : List.copyOf(group.canonicalPlantIds());
    }

    private List<org.springframework.ai.document.Document> denseSearch(
            String query, KnowledgeSource source, VectorStore store, List<String> canonicalPlantIds, int topK,
            RetrievalTraceCollector trace, String sourceTag, String scope, RagRuntimeConfig config) {
        SearchRequest.Builder requestBuilder = SearchRequest.builder().query(query)
                .topK(topK)
                .similarityThreshold(config.similarityThreshold());
        if (source == KnowledgeSource.PLANT && !canonicalPlantIds.isEmpty()) {
            FilterExpressionBuilder filters = new FilterExpressionBuilder();
            requestBuilder.filterExpression(canonicalPlantIds.size() == 1
                    ? filters.eq("canonicalPlantId", canonicalPlantIds.get(0)).build()
                    : filters.in("canonicalPlantId", canonicalPlantIds.toArray()).build());
        }
        SearchRequest request = requestBuilder.build();
        return trace.time("dense_search", sourceTag, scope,
                () -> metrics.time("embedding", sourceTag, () -> store.similaritySearch(request)));
    }

    private Map<String, Double> rerank(RetrievalRequest request, String query, List<LogicalEvidenceCandidate> candidates,
                                       RagRuntimeSnapshot runtimeSnapshot) {
        if (reranker instanceof RequestAwareSnapshotReranker requestAwareReranker) {
            return requestAwareReranker.rerank(request, query, candidates, runtimeSnapshot);
        }
        if (reranker instanceof SnapshotReranker snapshotReranker) {
            return snapshotReranker.rerank(query, candidates, runtimeSnapshot);
        }
        return reranker.rerank(query, candidates);
    }

    private RerankResult rerankCandidates(RetrievalRequest request, String query, List<LogicalEvidenceCandidate> candidates,
                                          RetrievalTraceCollector trace, RagRuntimeSnapshot runtimeSnapshot) {
        Map<String, Double> scores = trace.time("rerank", "all", "all",
                () -> metrics.time("rerank", "all", () -> rerank(request, query, candidates, runtimeSnapshot)));
        List<LogicalEvidenceCandidate> reranked = candidates.stream()
                .map(candidate -> candidate.withRerankedRepresentative(scores))
                .sorted(Comparator.comparingDouble((LogicalEvidenceCandidate candidate) -> scores
                        .getOrDefault(candidate.representativeFragmentId(), Double.NEGATIVE_INFINITY)).reversed())
                .toList();
        return new RerankResult(reranked, scores);
    }

    private record RetrievalPayload(List<Evidence> evidence,
                                    com.healingplanet.ai.domain.EntityResolutionDiagnostics entityResolution) { }

    private record SelectionResult(List<Evidence> evidence, Map<String, String> reasons) { }

    private record RecallState(List<LogicalEvidenceCandidate> candidates, RecallBudget budget,
                               Map<RecallRoute, List<LogicalEvidenceCandidate>> recalled) { }

    private record RerankResult(List<LogicalEvidenceCandidate> candidates, Map<String, Double> scores) { }

    private record RecallRoute(KnowledgeSource source, String groupId) { }

}
