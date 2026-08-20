package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.domain.DiseaseDetection;
import com.healingplanet.ai.domain.Evidence;
import com.healingplanet.ai.domain.EvidenceType;
import com.healingplanet.ai.domain.KnowledgeSource;
import com.healingplanet.ai.domain.RagQuery;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class DiseaseKnowledgeRetriever {
    private final VectorStore store;
    private final SparseIndexService sparseIndex;
    private final KnowledgeDocumentMapper documentMapper;
    private final Reranker reranker;
    private final RagProperties properties;

    public DiseaseKnowledgeRetriever(@Qualifier("diseaseVectorStore") VectorStore store,
                                     SparseIndexService sparseIndex, KnowledgeDocumentMapper documentMapper,
                                     Reranker reranker, RagProperties properties) {
        this.store = store;
        this.sparseIndex = sparseIndex;
        this.documentMapper = documentMapper;
        this.reranker = reranker;
        this.properties = properties;
    }

    public List<Evidence> retrieve(DiseaseDetection detection, RagQuery query) {
        if (detection.healthy()) return List.of();
        String searchText = searchText(detection, query);
        List<RrfFusion.DenseHit> dense = properties.getRetrievalMode().usesDense()
                ? denseSearch(searchText) : List.of();
        List<SparseIndexService.SparseHit> sparse = properties.getRetrievalMode().usesSparse()
                ? sparseIndex.search(KnowledgeSource.DISEASE, searchText, properties.getSparseTopK()) : List.of();
        List<RetrievalCandidate> candidates = RrfFusion.fuse(dense, sparse, properties.getRrfK());
        Map<String, Double> rerankScores = reranker.rerank(searchText, candidates);
        return candidates.stream().map(candidate -> toEvidence(candidate,
                        rerankScores.get(candidate.document().id()), detection))
                .sorted((left, right) -> Double.compare(right.finalScore(), left.finalScore()))
                .limit(Math.min(3, properties.getFinalTopK())).toList();
    }

    private List<RrfFusion.DenseHit> denseSearch(String searchText) {
        SearchRequest request = SearchRequest.builder().query(searchText)
                .topK(properties.getDenseTopK()).similarityThreshold(properties.getSimilarityThreshold()).build();
        return store.similaritySearch(request).stream()
                .map(document -> new RrfFusion.DenseHit(documentMapper.fromSpring(document, KnowledgeSource.DISEASE),
                        document.getScore() == null ? 0d : document.getScore())).toList();
    }

    private Evidence toEvidence(RetrievalCandidate candidate, Double rerank, DiseaseDetection detection) {
        var document = candidate.document();
        var ranking = properties.getSourceAwareRanking();
        double rrf = Math.min(1d, candidate.fusionScore() * ranking.getRrfNormalizationFactor());
        double retrieval = switch (properties.getRetrievalMode()) {
            case DENSE_ONLY -> candidate.denseScore() == null ? rrf : candidate.denseScore();
            case BM25_ONLY -> rrf;
            case HYBRID_RRF -> candidate.denseScore() == null ? rrf
                    : ranking.getDenseWeight() * candidate.denseScore() + ranking.getRrfWeight() * rrf;
        };
        double semantic = rerank == null ? retrieval : rerank;
        double nameMatch = matches(detection.diseaseName(), document.tags()) ? 1d : 0d;
        double score = clamp(0.65 * semantic + 0.25 * document.trustScore() + 0.10 * nameMatch);
        return new Evidence(document.id(), EvidenceType.DISEASE_KNOWLEDGE, document.sourceId(),
                KnowledgeSource.DISEASE.name(), document.title(), document.content(), retrieval, rerank,
                document.trustScore(), score, document.metadata(), document.createdAt());
    }

    private boolean matches(String diseaseName, List<String> tags) {
        if (diseaseName == null || diseaseName.isBlank()) return false;
        String value = diseaseName.toLowerCase();
        return tags.stream().map(String::toLowerCase)
                .anyMatch(tag -> value.contains(tag) || tag.contains(value));
    }

    private String searchText(DiseaseDetection detection, RagQuery query) {
        return String.join(" ", List.of(safe(detection.cropName()), safe(detection.diseaseName()),
                safe(detection.className()), safe(query.query()))).trim();
    }

    private String safe(String value) { return value == null ? "" : value; }
    private double clamp(double value) { return Math.max(0, Math.min(1, value)); }
}
