package com.healingplanet.ai.ingestion;

import com.healingplanet.ai.domain.IndexReport;
import com.healingplanet.ai.domain.KnowledgeDocument;
import com.healingplanet.ai.domain.KnowledgeSource;
import com.healingplanet.ai.retrieval.SparseIndexService;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class IngestionService {

    private final KnowledgeRepository repository;
    private final KnowledgeDocumentConverter converter;
    private final SparseIndexService sparseIndex;
    private final VectorStore plantVectorStore;
    private final VectorStore communityVectorStore;

    public IngestionService(KnowledgeRepository repository, KnowledgeDocumentConverter converter,
                            SparseIndexService sparseIndex,
                            @Qualifier("plantVectorStore") VectorStore plantVectorStore,
                            @Qualifier("communityVectorStore") VectorStore communityVectorStore) {
        this.repository = repository;
        this.converter = converter;
        this.sparseIndex = sparseIndex;
        this.plantVectorStore = plantVectorStore;
        this.communityVectorStore = communityVectorStore;
    }

    public IndexReport fullIndex() {
        IndexReport plant = indexPlants();
        IndexReport community = indexCommunity();
        return new IndexReport(plant.plantDocuments(), community.communityDocuments(),
                plant.deletedDocuments() + community.deletedDocuments());
    }

    public IndexReport indexPlants() {
        List<KnowledgeDocument> documents = repository.findPlants().stream()
                .flatMap(row -> converter.fromPlant(row).stream()).toList();
        int deleted = replace(KnowledgeSource.PLANT, documents, plantVectorStore);
        return IndexReport.plant(documents.size(), deleted);
    }

    public IndexReport indexCommunity() {
        List<KnowledgeDocument> documents = repository.findPublishedPosts().stream()
                .flatMap(row -> converter.fromPost(row).stream()).toList();
        int deleted = replace(KnowledgeSource.COMMUNITY, documents, communityVectorStore);
        return IndexReport.community(documents.size(), deleted);
    }

    public IndexReport indexPost(String postId) {
        Set<String> oldIds = sparseIndex.idsBySourceId(KnowledgeSource.COMMUNITY, postId);
        KnowledgeRepository.PostRow row = repository.findPublishedPost(postId);
        if (row == null) {
            deleteIds(KnowledgeSource.COMMUNITY, oldIds, communityVectorStore);
            return IndexReport.community(0, oldIds.size());
        }
        List<KnowledgeDocument> documents = converter.fromPost(row);
        Set<String> newIds = documents.stream().map(KnowledgeDocument::id)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> staleIds = new HashSet<>(oldIds);
        staleIds.removeAll(newIds);
        deleteIds(KnowledgeSource.COMMUNITY, staleIds, communityVectorStore);
        communityVectorStore.add(toSpringDocuments(documents));
        documents.forEach(sparseIndex::upsert);
        return IndexReport.community(documents.size(), staleIds.size());
    }

    public IndexReport deletePost(String postId) {
        Set<String> ids = sparseIndex.idsBySourceId(KnowledgeSource.COMMUNITY, postId);
        deleteIds(KnowledgeSource.COMMUNITY, ids, communityVectorStore);
        return IndexReport.community(0, ids.size());
    }

    private int replace(KnowledgeSource source, List<KnowledgeDocument> documents, VectorStore vectorStore) {
        Set<String> existing = sparseIndex.ids(source);
        Set<String> incoming = documents.stream().map(KnowledgeDocument::id)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> stale = new HashSet<>(existing);
        stale.removeAll(incoming);
        deleteIds(source, stale, vectorStore);
        if (!documents.isEmpty()) vectorStore.add(toSpringDocuments(documents));
        sparseIndex.replaceAll(source, documents);
        return stale.size();
    }

    private void deleteIds(KnowledgeSource source, Set<String> ids, VectorStore vectorStore) {
        if (ids.isEmpty()) return;
        vectorStore.delete(new ArrayList<>(ids));
        ids.forEach(id -> sparseIndex.delete(source, id));
    }

    private List<Document> toSpringDocuments(List<KnowledgeDocument> documents) {
        return documents.stream().map(document -> new Document(
                document.id(), document.content(), document.metadata())).toList();
    }
}
