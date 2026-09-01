package com.healingplanet.ai.retrieval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.domain.KnowledgeDocument;
import com.healingplanet.ai.domain.KnowledgeSource;
import jakarta.annotation.PreDestroy;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.MultiBits;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class SparseIndexService {

    private final ObjectMapper objectMapper;
    private final ChineseNgramAnalyzer analyzer;
    private final BM25Similarity similarity;
    private final Map<KnowledgeSource, Directory> directories = new EnumMap<>(KnowledgeSource.class);
    private final Map<KnowledgeSource, SearcherManager> searchers = new EnumMap<>(KnowledgeSource.class);

    public SparseIndexService(RagProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        var bm25 = properties.getBm25();
        this.analyzer = new ChineseNgramAnalyzer(bm25.getMinNgram(), bm25.getMaxNgram());
        this.similarity = new BM25Similarity(bm25.getK1(), bm25.getB());
        try {
            Files.createDirectories(properties.getDataDirectory());
            for (KnowledgeSource source : KnowledgeSource.values()) {
                var path = properties.getDataDirectory().resolve(source.name().toLowerCase() + "-sparse");
                Files.createDirectories(path);
                Directory directory = FSDirectory.open(path);
                directories.put(source, directory);
                ensureIndexExists(directory);
                searchers.put(source, new SearcherManager(directory, new SearcherFactory()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("无法初始化稀疏检索索引", e);
        }
    }

    public synchronized void replaceAll(KnowledgeSource source, List<KnowledgeDocument> documents) {
        try (IndexWriter writer = writer(source)) {
            writer.deleteAll();
            for (KnowledgeDocument document : documents) writer.addDocument(toLucene(document));
            writer.commit();
            refreshSearcher(source);
        } catch (IOException e) {
            throw new UncheckedIOException("重建稀疏索引失败", e);
        }
    }

    public synchronized void upsert(KnowledgeDocument document) {
        try (IndexWriter writer = writer(document.source())) {
            writer.updateDocument(new Term("id", document.id()), toLucene(document));
            writer.commit();
            refreshSearcher(document.source());
        } catch (IOException e) {
            throw new UncheckedIOException("更新稀疏索引失败", e);
        }
    }

    public synchronized void upsertAll(List<KnowledgeDocument> documents) {
        if (documents.isEmpty()) return;
        try (IndexWriter writer = writer(documents.get(0).source())) {
            for (KnowledgeDocument document : documents) {
                writer.updateDocument(new Term("id", document.id()), toLucene(document));
            }
            writer.commit();
            refreshSearcher(documents.get(0).source());
        } catch (IOException e) {
            throw new UncheckedIOException("批量更新稀疏索引失败", e);
        }
    }

    public synchronized void delete(KnowledgeSource source, String id) {
        try (IndexWriter writer = writer(source)) {
            writer.deleteDocuments(new Term("id", id));
            writer.commit();
            refreshSearcher(source);
        } catch (IOException e) {
            throw new UncheckedIOException("删除稀疏索引失败", e);
        }
    }

    public synchronized void deleteAll(KnowledgeSource source, List<String> ids) {
        if (ids.isEmpty()) return;
        try (IndexWriter writer = writer(source)) {
            writer.deleteDocuments(ids.stream().map(id -> new Term("id", id)).toArray(Term[]::new));
            writer.commit();
            refreshSearcher(source);
        } catch (IOException e) {
            throw new UncheckedIOException("批量删除稀疏索引失败", e);
        }
    }

    public synchronized Set<String> ids(KnowledgeSource source) {
        Set<String> result = new HashSet<>();
        try {
            if (!DirectoryReader.indexExists(directories.get(source))) return result;
            try (DirectoryReader reader = DirectoryReader.open(directories.get(source))) {
                var liveDocs = MultiBits.getLiveDocs(reader);
                for (int i = 0; i < reader.maxDoc(); i++) {
                    if (liveDocs == null || liveDocs.get(i)) {
                        result.add(reader.storedFields().document(i).get("id"));
                    }
                }
            }
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException("读取稀疏索引失败", e);
        }
    }

    public synchronized Set<String> idsBySourceId(KnowledgeSource source, String sourceId) {
        Set<String> result = new HashSet<>();
        try {
            if (!DirectoryReader.indexExists(directories.get(source))) return result;
            try (DirectoryReader reader = DirectoryReader.open(directories.get(source))) {
                var liveDocs = MultiBits.getLiveDocs(reader);
                for (int i = 0; i < reader.maxDoc(); i++) {
                    if (liveDocs == null || liveDocs.get(i)) {
                        Document document = reader.storedFields().document(i);
                        if (sourceId.equals(document.get("sourceId"))) result.add(document.get("id"));
                    }
                }
            }
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException("读取稀疏索引失败", e);
        }
    }

    public synchronized Map<String, KnowledgeDocument> documentsByIds(KnowledgeSource source, Set<String> ids) {
        if (ids.isEmpty()) return Map.of();
        try {
            if (!DirectoryReader.indexExists(directories.get(source))) return Map.of();
            try (DirectoryReader reader = DirectoryReader.open(directories.get(source))) {
                BooleanQuery.Builder query = new BooleanQuery.Builder();
                ids.forEach(id -> query.add(new TermQuery(new Term("id", id)), BooleanClause.Occur.SHOULD));
                var hits = new IndexSearcher(reader).search(query.build(), ids.size()).scoreDocs;
                Map<String, KnowledgeDocument> result = new java.util.HashMap<>();
                for (ScoreDoc hit : hits) {
                    KnowledgeDocument document = fromLucene(reader.storedFields().document(hit.doc));
                    result.put(document.id(), document);
                }
                return result;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("读取稀疏索引文档失败", e);
        }
    }

    public List<SparseHit> search(KnowledgeSource source, String query, int topK) {
        return search(source, query, topK, List.of());
    }

    /** Applies canonicalPlantId constraints inside Lucene before Top-K is selected. */
    public List<SparseHit> search(KnowledgeSource source, String query, int topK,
                                  List<String> canonicalPlantIds) {
        SearcherManager manager = searchers.get(source);
        IndexSearcher searcher = null;
        try {
            searcher = manager.acquire();
            searcher.setSimilarity(similarity);
            var parser = new QueryParser("searchText", analyzer);
            var parsed = parser.parse(QueryParser.escape(query));
            org.apache.lucene.search.Query searchQuery = parsed;
            if (canonicalPlantIds != null && !canonicalPlantIds.isEmpty()) {
                BooleanQuery.Builder filtered = new BooleanQuery.Builder().add(parsed, BooleanClause.Occur.MUST);
                BooleanQuery.Builder ids = new BooleanQuery.Builder();
                canonicalPlantIds.forEach(id -> ids.add(new TermQuery(new Term("canonicalPlantId", id)),
                        BooleanClause.Occur.SHOULD));
                filtered.add(ids.build(), BooleanClause.Occur.FILTER);
                searchQuery = filtered.build();
            }
            ScoreDoc[] hits = searcher.search(searchQuery, topK).scoreDocs;
            List<SparseHit> result = new ArrayList<>(hits.length);
            for (ScoreDoc hit : hits) {
                result.add(new SparseHit(fromLucene(searcher.getIndexReader().storedFields().document(hit.doc)), hit.score));
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("稀疏检索失败", e);
        } finally {
            if (searcher != null) try { manager.release(searcher); } catch (IOException ignored) { }
        }
    }

    /** Single ngram/BM25 lookup over indexed entity names. */
    public List<SparseHit> searchEntityNames(String normalizedQuery, int topK) {
        if (normalizedQuery == null || normalizedQuery.length() < 2) return List.of();
        SearcherManager manager = searchers.get(KnowledgeSource.PLANT_ENTITY);
        IndexSearcher searcher = null;
        try {
            searcher = manager.acquire();
            searcher.setSimilarity(similarity);
            String escaped = QueryParser.escape(normalizedQuery);
            org.apache.lucene.search.Query entityNameQuery = new QueryParser("entityNameText", analyzer).parse(escaped);
            org.apache.lucene.search.Query legacyQuery = new QueryParser("searchText", analyzer).parse(escaped);
            org.apache.lucene.search.Query query = new BooleanQuery.Builder()
                    .add(entityNameQuery, BooleanClause.Occur.SHOULD)
                    .add(legacyQuery, BooleanClause.Occur.SHOULD)
                    .setMinimumNumberShouldMatch(1)
                    .build();
            ScoreDoc[] hits = searcher.search(query, topK).scoreDocs;
            List<SparseHit> result = new ArrayList<>(hits.length);
            for (ScoreDoc hit : hits) {
                result.add(new SparseHit(fromLucene(searcher.getIndexReader().storedFields().document(hit.doc)), hit.score));
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("植物实体模糊检索失败", e);
        } finally {
            if (searcher != null) try { manager.release(searcher); } catch (IOException ignored) { }
        }
    }

    private void ensureIndexExists(Directory directory) throws IOException {
        if (DirectoryReader.indexExists(directory)) return;
        try (IndexWriter writer = new IndexWriter(directory,
                new IndexWriterConfig(analyzer).setSimilarity(similarity))) {
            writer.commit();
        }
    }

    private void refreshSearcher(KnowledgeSource source) throws IOException {
        searchers.get(source).maybeRefreshBlocking();
    }

    private IndexWriter writer(KnowledgeSource source) throws IOException {
        return new IndexWriter(directories.get(source),
                new IndexWriterConfig(analyzer).setSimilarity(similarity));
    }

    private Document toLucene(KnowledgeDocument source) {
        Document document = new Document();
        document.add(new StringField("id", source.id(), Field.Store.YES));
        document.add(new TextField("searchText", source.title() + "\n" + source.embeddingText() + "\n" +
                source.plantName() + "\n" + String.join(" ", source.tags()), Field.Store.NO));
        if (source.source() == KnowledgeSource.PLANT_ENTITY) {
            String names = source.attributes().getOrDefault("normalizedNames", "");
            document.add(new TextField("entityNameText", names.replace('|', ' '), Field.Store.NO));
            for (String name : names.split("\\|")) {
                if (!name.isBlank()) document.add(new StringField("entityName", name, Field.Store.NO));
            }
        }
        put(document, "source", source.source().name());
        put(document, "sourceId", source.sourceId());
        put(document, "title", source.title());
        put(document, "embeddingText", source.embeddingText());
        put(document, "displayContent", source.displayContent());
        put(document, "canonicalPlantId", source.canonicalPlantId());
        put(document, "plantName", source.plantName());
        put(document, "knowledgeType", source.knowledgeType());
        put(document, "tags", json(source.tags()));
        put(document, "trustScore", Double.toString(source.trustScore()));
        put(document, "essence", Boolean.toString(source.essence()));
        put(document, "likes", Integer.toString(source.likes()));
        put(document, "collects", Integer.toString(source.collects()));
        put(document, "comments", Integer.toString(source.comments()));
        put(document, "views", Integer.toString(source.views()));
        put(document, "createdAt", source.createdAt() == null ? "" : source.createdAt().toString());
        put(document, "attributes", json(source.attributes()));
        return document;
    }

    private KnowledgeDocument fromLucene(Document d) {
        try {
            return new KnowledgeDocument(
                    d.get("id"), KnowledgeSource.valueOf(d.get("source")), d.get("sourceId"),
                    d.get("title"), valueOrLegacy(d, "embeddingText"), valueOrLegacy(d, "displayContent"),
                    d.get("canonicalPlantId"), d.get("plantName"),
                    d.get("knowledgeType"), objectMapper.readValue(d.get("tags"), new TypeReference<>() {}),
                    Double.parseDouble(d.get("trustScore")), Boolean.parseBoolean(d.get("essence")),
                    Integer.parseInt(d.get("likes")), Integer.parseInt(d.get("collects")),
                    Integer.parseInt(d.get("comments")), Integer.parseInt(d.get("views")),
                    d.get("createdAt").isBlank() ? null : Instant.parse(d.get("createdAt")),
                    readAttributes(d.get("attributes"))
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("稀疏索引元数据损坏", e);
        }
    }

    private Map<String, String> readAttributes(String value) throws JsonProcessingException {
        if (value == null || value.isBlank()) return Map.of();
        return objectMapper.readValue(value, new TypeReference<>() {});
    }

    private String valueOrLegacy(Document document, String name) {
        String value = document.get(name);
        return value == null ? document.get("content") : value;
    }

    private void put(Document document, String name, String value) {
        document.add(new StringField(name, value == null ? "" : value, Field.Store.YES));
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("无法序列化索引元数据", e); }
    }

    @PreDestroy
    void close() {
        searchers.values().forEach(manager -> {
            try { manager.close(); } catch (IOException ignored) { }
        });
        directories.values().forEach(directory -> {
            try { directory.close(); } catch (IOException ignored) { }
        });
        analyzer.close();
    }

    public record SparseHit(KnowledgeDocument document, double score) { }
}
