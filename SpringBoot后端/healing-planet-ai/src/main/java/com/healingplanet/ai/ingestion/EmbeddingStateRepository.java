package com.healingplanet.ai.ingestion;

import com.healingplanet.ai.domain.KnowledgeSource;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 持久化已写入向量库的内容指纹。不能以本机 Lucene 索引代替该状态，
 * 否则多实例或本地索引丢失时会错误地重复调用 embedding 服务。
 */
@Repository
public class EmbeddingStateRepository {

    private static final String UPSERT_SQL = """
            insert into rag_embedding_state
                    (document_id, source, source_id, content_hash, embedding_model_version,
                     embedding_content_version, chunk_schema_version, index_fingerprint, payload_hash)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?)
            on duplicate key update
                    source = values(source),
                    source_id = values(source_id),
                    content_hash = values(content_hash),
                    embedding_model_version = values(embedding_model_version),
                    embedding_content_version = values(embedding_content_version),
                    chunk_schema_version = values(chunk_schema_version),
                    index_fingerprint = values(index_fingerprint),
                    payload_hash = values(payload_hash),
                    updated_at = current_timestamp
            """;

    private final JdbcTemplate jdbcTemplate;

    public EmbeddingStateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, EmbeddingState> findByDocumentIds(Collection<String> documentIds) {
        if (documentIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = documentIds.stream().map(id -> "?").collect(Collectors.joining(", "));
        List<EmbeddingState> states = jdbcTemplate.query("""
                select document_id, source, source_id, content_hash, embedding_model_version,
                       embedding_content_version, chunk_schema_version, index_fingerprint, payload_hash
                from rag_embedding_state
                where document_id in (""" + placeholders + ")", (rs, rowNum) -> new EmbeddingState(
                rs.getString("document_id"), KnowledgeSource.valueOf(rs.getString("source")),
                rs.getString("source_id"), rs.getString("content_hash"),
                rs.getString("embedding_model_version"), rs.getString("embedding_content_version"),
                rs.getString("chunk_schema_version"), rs.getString("index_fingerprint"),
                rs.getString("payload_hash")), documentIds.toArray());
        Map<String, EmbeddingState> result = new HashMap<>();
        states.forEach(state -> result.put(state.documentId(), state));
        return result;
    }

    public Set<String> documentIdsBySource(KnowledgeSource source) {
        return new LinkedHashSet<>(jdbcTemplate.queryForList("""
                select document_id
                from rag_embedding_state
                where source = ?
                """, String.class, source.name()));
    }

    public Set<String> documentIdsBySourceId(KnowledgeSource source, String sourceId) {
        return new LinkedHashSet<>(jdbcTemplate.queryForList("""
                select document_id
                from rag_embedding_state
                where source = ? and source_id = ?
                """, String.class, source.name(), sourceId));
    }

    public void upsertAll(List<EmbeddingState> states) {
        if (states.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(UPSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement statement, int index) throws SQLException {
                EmbeddingState state = states.get(index);
                statement.setString(1, state.documentId());
                statement.setString(2, state.source().name());
                statement.setString(3, state.sourceId());
                statement.setString(4, state.contentHash());
                statement.setString(5, state.embeddingModelVersion());
                statement.setString(6, state.embeddingContentVersion());
                statement.setString(7, state.chunkSchemaVersion());
                statement.setString(8, state.indexFingerprint());
                statement.setString(9, state.payloadHash());
            }

            @Override
            public int getBatchSize() {
                return states.size();
            }
        });
    }

    public void deleteByDocumentIds(Collection<String> documentIds) {
        if (documentIds.isEmpty()) {
            return;
        }
        String placeholders = documentIds.stream().map(id -> "?").collect(Collectors.joining(", "));
        jdbcTemplate.update("delete from rag_embedding_state where document_id in (" + placeholders + ")",
                documentIds.toArray());
    }

    public record EmbeddingState(String documentId, KnowledgeSource source, String sourceId,
                                 String contentHash, String embeddingModelVersion,
                                 String embeddingContentVersion, String chunkSchemaVersion,
                                 String indexFingerprint, String payloadHash) {
        /** Compatibility constructor for rows written before payload compatibility was tracked. */
        public EmbeddingState(String documentId, KnowledgeSource source, String sourceId,
                              String contentHash, String embeddingModelVersion, String embeddingContentVersion,
                              String chunkSchemaVersion, String indexFingerprint) {
            this(documentId, source, sourceId, contentHash, embeddingModelVersion, embeddingContentVersion,
                    chunkSchemaVersion, indexFingerprint, "");
        }

        /** 兼容仅以模型版本去重的旧测试和调用方。 */
        public EmbeddingState(String documentId, KnowledgeSource source, String sourceId,
                              String contentHash, String embeddingModelVersion) {
            this(documentId, source, sourceId, contentHash, embeddingModelVersion,
                    "embedding-content-v2", "chunk-schema-v2",
                    new IndexFingerprint(embeddingModelVersion, "embedding-content-v2", "chunk-schema-v2").value(),
                    "");
        }
    }
}
