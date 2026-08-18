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
import java.util.List;
import java.util.Map;

@Service
public class HybridEvidenceRetriever implements EvidenceRetriever {

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
        return metrics.time("knowledge_total", "all", () -> retrieveTimed(query));
    }

    private List<Evidence> retrieveTimed(RagQuery query) {
        PlantEntityResolver.Resolution entity = metrics.time("entity_resolve", "all",
                () -> entityResolver.resolve(query));
        if (entity.kind() == PlantEntityResolver.ResolutionKind.UNKNOWN
                || entity.kind() == PlantEntityResolver.ResolutionKind.OUT_OF_DOMAIN) {
            metrics.recordCandidates("selected", "all", 0);
            return List.of();
        }

        List<RetrievalCandidate> fused = new ArrayList<>();
        boolean includePlant = booleanContext(query, "includePlantKnowledge", query.intent() != QueryIntent.COMMUNITY_SEARCH);
        boolean includeCommunity = booleanContext(query, "includeCommunity", true);
        if (includePlant) {
            fused.addAll(retrieveSource(query.query(), KnowledgeSource.PLANT, plantStore, entity));
        }
        if (includeCommunity) {
            fused.addAll(retrieveSource(query.query(), KnowledgeSource.COMMUNITY, communityStore, entity));
        }
        metrics.recordCandidates("fused", "all", fused.size());
        List<RetrievalCandidate> filtered = filterKnowledgeType(query, fused);
        Map<String, Double> rerankScores = metrics.time("rerank", "all",
                () -> reranker.rerank(query.query(), filtered));
        List<Evidence> selected = metrics.time("final_rank", "all",
                () -> ranker.rank(query, filtered, rerankScores, properties.getFinalTopK()));
        metrics.recordCandidates("selected", "all", selected.size());
        return selected;
    }

    private List<RetrievalCandidate> filterKnowledgeType(RagQuery query, List<RetrievalCandidate> candidates) {
        Object required = query.context().get("requiredKnowledgeType");
        if (!(required instanceof String type) || type.isBlank()) return candidates;
        return candidates.stream().filter(candidate -> type.equalsIgnoreCase(candidate.document().knowledgeType())).toList();
    }

    private boolean booleanContext(RagQuery query, String key, boolean defaultValue) {
        Object value = query.context().get(key);
        return value instanceof Boolean bool ? bool : defaultValue;
    }

    private List<RetrievalCandidate> retrieveSource(String query, KnowledgeSource source, VectorStore store,
                                                    PlantEntityResolver.Resolution entity) {
        SearchRequest.Builder requestBuilder = SearchRequest.builder().query(query)
                .topK(properties.getDenseTopK())
                .similarityThreshold(properties.getSimilarityThreshold());
        if (source == KnowledgeSource.PLANT && entity.kind() == PlantEntityResolver.ResolutionKind.KNOWN) {
            requestBuilder.filterExpression(new FilterExpressionBuilder()
                    .eq("canonicalPlantId", entity.canonicalPlantId()).build());
        }
        SearchRequest request = requestBuilder.build();
        String sourceTag = source.name().toLowerCase(java.util.Locale.ROOT);
        List<RrfFusion.DenseHit> dense = metrics.time("dense_search", sourceTag,
                () -> store.similaritySearch(request).stream()
                .map(document -> new RrfFusion.DenseHit(documentMapper.fromSpring(document, source),
                        document.getScore() == null ? 0d : document.getScore()))
                .filter(hit -> entityResolver.matches(entity, hit.document()))
                .toList());
        List<SparseIndexService.SparseHit> sparse = metrics.time("sparse_search", sourceTag,
                () -> sparseIndex.search(source, query, properties.getSparseTopK()).stream()
                        .filter(hit -> entityResolver.matches(entity, hit.document()))
                        .toList());
        metrics.recordCandidates("dense", sourceTag, dense.size());
        metrics.recordCandidates("sparse", sourceTag, sparse.size());
        List<RetrievalCandidate> result = metrics.time("rrf_fusion", sourceTag,
                () -> RrfFusion.fuse(dense, sparse));
        metrics.recordCandidates("fused", sourceTag, result.size());
        return result;
    }
}
