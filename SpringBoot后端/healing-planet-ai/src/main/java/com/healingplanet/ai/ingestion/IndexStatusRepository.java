package com.healingplanet.ai.ingestion;

import com.healingplanet.ai.domain.IndexOperation;
import com.healingplanet.ai.domain.KnowledgeSource;
import com.healingplanet.ai.domain.SourceIndexRunReport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Database-backed latest status so the view remains useful across application instances and restarts. */
@Repository
public class IndexStatusRepository implements IndexRunStatusStore {
    private final JdbcTemplate jdbcTemplate;

    public IndexStatusRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void markRunning(KnowledgeSource source, String runId, IndexOperation operation, Instant startedAt,
                            String fingerprint) {
        jdbcTemplate.update("""
                insert into rag_index_status
                        (source, last_run_id, last_operation, last_attempt_started_at, last_run_status,
                         last_index_fingerprint, last_error)
                values (?, ?, ?, ?, 'RUNNING', ?, '')
                on duplicate key update
                        last_run_id = values(last_run_id),
                        last_operation = values(last_operation),
                        last_attempt_started_at = values(last_attempt_started_at),
                        last_attempt_finished_at = null,
                        last_run_status = 'RUNNING',
                        last_index_fingerprint = values(last_index_fingerprint),
                        last_error = '',
                        updated_at = current_timestamp(3)
                """, source.name(), runId, operation.name(), timestamp(startedAt), fingerprint);
    }

    @Override
    public void markSucceeded(SourceIndexRunReport report, String runId, IndexOperation operation, Instant startedAt,
                              Instant completedAt, String fingerprint) {
        jdbcTemplate.update("""
                insert into rag_index_status
                        (source, last_run_id, last_operation, last_attempt_started_at, last_attempt_finished_at,
                         last_successful_index_at, last_run_status, last_index_fingerprint,
                         documents_seen, documents_unchanged, documents_embedded, payload_updates, sparse_updates,
                         documents_deleted, fragments_created, logical_evidences_created, failed_documents, last_error)
                values (?, ?, ?, ?, ?, ?, 'SUCCEEDED', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '')
                on duplicate key update
                        last_run_id = values(last_run_id),
                        last_operation = values(last_operation),
                        last_attempt_started_at = values(last_attempt_started_at),
                        last_attempt_finished_at = values(last_attempt_finished_at),
                        last_successful_index_at = values(last_successful_index_at),
                        last_run_status = 'SUCCEEDED',
                        last_index_fingerprint = values(last_index_fingerprint),
                        documents_seen = values(documents_seen),
                        documents_unchanged = values(documents_unchanged),
                        documents_embedded = values(documents_embedded),
                        payload_updates = values(payload_updates),
                        sparse_updates = values(sparse_updates),
                        documents_deleted = values(documents_deleted),
                        fragments_created = values(fragments_created),
                        logical_evidences_created = values(logical_evidences_created),
                        failed_documents = values(failed_documents),
                        last_error = '',
                        updated_at = current_timestamp(3)
                """, report.source().name(), runId, operation.name(), timestamp(startedAt), timestamp(completedAt),
                timestamp(completedAt), fingerprint, report.documentsSeen(), report.documentsUnchanged(),
                report.documentsEmbedded(), report.payloadUpdates(), report.sparseUpdates(), report.documentsDeleted(),
                report.fragmentsCreated(), report.logicalEvidencesCreated(), report.failedDocuments());
    }

    @Override
    public void markFailed(KnowledgeSource source, String runId, IndexOperation operation, Instant startedAt,
                           Instant completedAt, String fingerprint, int failedDocuments, String failureReason) {
        jdbcTemplate.update("""
                insert into rag_index_status
                        (source, last_run_id, last_operation, last_attempt_started_at, last_attempt_finished_at,
                         last_run_status, last_index_fingerprint, failed_documents, last_error)
                values (?, ?, ?, ?, ?, 'FAILED', ?, ?, ?)
                on duplicate key update
                        last_run_id = values(last_run_id),
                        last_operation = values(last_operation),
                        last_attempt_started_at = values(last_attempt_started_at),
                        last_attempt_finished_at = values(last_attempt_finished_at),
                        last_run_status = 'FAILED',
                        last_index_fingerprint = values(last_index_fingerprint),
                        failed_documents = values(failed_documents),
                        last_error = values(last_error),
                        updated_at = current_timestamp(3)
                """, source.name(), runId, operation.name(), timestamp(startedAt), timestamp(completedAt),
                fingerprint, failedDocuments, truncate(failureReason));
    }

    public Map<KnowledgeSource, PersistedStatus> findAll() {
        List<PersistedStatus> rows = jdbcTemplate.query("""
                select source, last_run_id, last_operation, last_attempt_started_at, last_attempt_finished_at,
                       last_successful_index_at, last_run_status, last_index_fingerprint, documents_seen,
                       documents_unchanged, documents_embedded, payload_updates, sparse_updates, documents_deleted,
                       fragments_created, logical_evidences_created, failed_documents, last_error
                from rag_index_status
                """, (rs, rowNum) -> new PersistedStatus(
                KnowledgeSource.valueOf(rs.getString("source")), rs.getString("last_run_id"),
                rs.getString("last_operation"), instant(rs.getTimestamp("last_attempt_started_at")),
                instant(rs.getTimestamp("last_attempt_finished_at")), instant(rs.getTimestamp("last_successful_index_at")),
                rs.getString("last_run_status"), rs.getString("last_index_fingerprint"),
                rs.getInt("documents_seen"), rs.getInt("documents_unchanged"), rs.getInt("documents_embedded"),
                rs.getInt("payload_updates"), rs.getInt("sparse_updates"), rs.getInt("documents_deleted"),
                rs.getInt("fragments_created"), rs.getInt("logical_evidences_created"), rs.getInt("failed_documents"),
                rs.getString("last_error")));
        Map<KnowledgeSource, PersistedStatus> result = new EnumMap<>(KnowledgeSource.class);
        rows.forEach(row -> result.put(row.source(), row));
        return result;
    }

    public Map<KnowledgeSource, EmbeddingStateStats> embeddingStateStats(String fingerprint) {
        List<EmbeddingStateStats> rows = jdbcTemplate.query("""
                select source, count(*) indexed_fragments,
                       coalesce(sum(case when index_fingerprint <> ? then 1 else 0 end), 0) stale_fingerprint_fragments
                from rag_embedding_state
                group by source
                """, (rs, rowNum) -> new EmbeddingStateStats(KnowledgeSource.valueOf(rs.getString("source")),
                rs.getLong("indexed_fragments"), rs.getLong("stale_fingerprint_fragments")), fingerprint);
        Map<KnowledgeSource, EmbeddingStateStats> result = new EnumMap<>(KnowledgeSource.class);
        rows.forEach(row -> result.put(row.source(), row));
        return result;
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private String truncate(String value) {
        if (value == null) return "";
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    public record PersistedStatus(KnowledgeSource source, String runId, String operation, Instant lastAttemptStartedAt,
                                  Instant lastAttemptFinishedAt, Instant lastSuccessfulIndexAt, String lastRunStatus,
                                  String lastIndexFingerprint, int documentsSeen, int documentsUnchanged,
                                  int documentsEmbedded, int payloadUpdates, int sparseUpdates, int documentsDeleted,
                                  int fragmentsCreated, int logicalEvidencesCreated, int failedDocuments,
                                  String lastError) {
    }

    public record EmbeddingStateStats(KnowledgeSource source, long indexedFragments,
                                      long staleFingerprintFragments) {
    }
}
