package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.domain.Evidence;
import com.healingplanet.ai.domain.KnowledgeSource;
import com.healingplanet.ai.domain.QueryIntent;
import com.healingplanet.ai.domain.RagQuery;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
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
    private final RagProperties properties;

    public HybridEvidenceRetriever(@Qualifier("plantVectorStore") VectorStore plantStore,
                                   @Qualifier("communityVectorStore") VectorStore communityStore,
                                   SparseIndexService sparseIndex, KnowledgeDocumentMapper documentMapper,
                                   Reranker reranker, SourceAwareRanker ranker, RagProperties properties) {
        this.plantStore = plantStore;
        this.communityStore = communityStore;
        this.sparseIndex = sparseIndex;
        this.documentMapper = documentMapper;
        this.reranker = reranker;
        this.ranker = ranker;
        this.properties = properties;
    }

    @Override
    public List<Evidence> retrieve(RagQuery query) {
        List<RetrievalCandidate> fused = new ArrayList<>();
        boolean includePlant = booleanContext(query, "includePlantKnowledge", query.intent() != QueryIntent.COMMUNITY_SEARCH);
        boolean includeCommunity = booleanContext(query, "includeCommunity", true);
        if (includePlant) {
            fused.addAll(retrieveSource(query.query(), KnowledgeSource.PLANT, plantStore));
        }
        if (includeCommunity) fused.addAll(retrieveSource(query.query(), KnowledgeSource.COMMUNITY, communityStore));
        Map<String, Double> rerankScores = reranker.rerank(query.query(), fused);
        return ranker.rank(query, fused, rerankScores, properties.getFinalTopK());
    }

    private boolean booleanContext(RagQuery query, String key, boolean defaultValue) {
        Object value = query.context().get(key);
        return value instanceof Boolean bool ? bool : defaultValue;
    }

    private List<RetrievalCandidate> retrieveSource(String query, KnowledgeSource source, VectorStore store) {
        SearchRequest request = SearchRequest.builder().query(query)
                .topK(properties.getDenseTopK())
                .similarityThreshold(properties.getSimilarityThreshold())
                .build();
        List<RrfFusion.DenseHit> dense = store.similaritySearch(request).stream()
                .map(document -> new RrfFusion.DenseHit(documentMapper.fromSpring(document, source),
                        document.getScore() == null ? 0d : document.getScore()))
                .toList();
        var sparse = sparseIndex.search(source, query, properties.getSparseTopK());
        return RrfFusion.fuse(dense, sparse);
    }
}
