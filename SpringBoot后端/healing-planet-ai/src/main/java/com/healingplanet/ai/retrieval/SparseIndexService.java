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
                directories.put(source, FSDirectory.open(path));
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
        } catch (IOException e) {
            throw new UncheckedIOException("重建稀疏索引失败", e);
        }
    }

    public synchronized void upsert(KnowledgeDocument document) {
        try (IndexWriter writer = writer(document.source())) {
            writer.updateDocument(new Term("id", document.id()), toLucene(document));
            writer.commit();
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
        } catch (IOException e) {
            throw new UncheckedIOException("批量更新稀疏索引失败", e);
        }
    }

    public synchronized void delete(KnowledgeSource source, String id) {
        try (IndexWriter writer = writer(source)) {
            writer.deleteDocuments(new Term("id", id));
            writer.commit();
        } catch (IOException e) {
            throw new UncheckedIOException("删除稀疏索引失败", e);
        }
    }

    public synchronized void deleteAll(KnowledgeSource source, List<String> ids) {
        if (ids.isEmpty()) return;
        try (IndexWriter writer = writer(source)) {
            writer.deleteDocuments(ids.stream().map(id -> new Term("id", id)).toArray(Term[]::new));
            writer.commit();
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

    public synchronized List<SparseHit> search(KnowledgeSource source, String query, int topK) {
        try {
            if (!DirectoryReader.indexExists(directories.get(source))) return List.of();
            try (DirectoryReader reader = DirectoryReader.open(directories.get(source))) {
                var searcher = new IndexSearcher(reader);
                searcher.setSimilarity(similarity);
                var parser = new QueryParser("searchText", analyzer);
                var parsed = parser.parse(QueryParser.escape(query));
                ScoreDoc[] hits = searcher.search(parsed, topK).scoreDocs;
                List<SparseHit> result = new ArrayList<>(hits.length);
                for (ScoreDoc hit : hits) {
                    result.add(new SparseHit(fromLucene(reader.storedFields().document(hit.doc)), hit.score));
                }
                return result;
            }
        } catch (Exception e) {
            throw new IllegalStateException("稀疏检索失败", e);
        }
    }

    private IndexWriter writer(KnowledgeSource source) throws IOException {
        return new IndexWriter(directories.get(source),
                new IndexWriterConfig(analyzer).setSimilarity(similarity));
    }

    private Document toLucene(KnowledgeDocument source) {
        Document document = new Document();
        document.add(new StringField("id", source.id(), Field.Store.YES));
        document.add(new TextField("searchText", source.title() + "\n" + source.content() + "\n" +
                source.plantName() + "\n" + String.join(" ", source.tags()), Field.Store.NO));
        put(document, "source", source.source().name());
        put(document, "sourceId", source.sourceId());
        put(document, "title", source.title());
        put(document, "content", source.content());
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
                    d.get("title"), d.get("content"), d.get("canonicalPlantId"), d.get("plantName"),
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

    private void put(Document document, String name, String value) {
        document.add(new StringField(name, value == null ? "" : value, Field.Store.YES));
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("无法序列化索引元数据", e); }
    }

    @PreDestroy
    void close() {
        directories.values().forEach(directory -> {
            try { directory.close(); } catch (IOException ignored) { }
        });
        analyzer.close();
    }

    public record SparseHit(KnowledgeDocument document, double score) { }
}
