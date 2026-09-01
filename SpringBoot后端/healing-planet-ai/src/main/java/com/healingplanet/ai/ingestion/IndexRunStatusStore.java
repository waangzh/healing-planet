package com.healingplanet.ai.ingestion;

import com.healingplanet.ai.domain.IndexOperation;
import com.healingplanet.ai.domain.SourceIndexRunReport;
import com.healingplanet.ai.domain.KnowledgeSource;

import java.time.Instant;

/** Writes only the latest per-source run state; detailed reports stay in the response and metrics stream. */
interface IndexRunStatusStore {
    void markRunning(KnowledgeSource source, String runId, IndexOperation operation, Instant startedAt,
                     String fingerprint);

    void markSucceeded(SourceIndexRunReport report, String runId, IndexOperation operation, Instant startedAt,
                       Instant completedAt, String fingerprint);

    void markFailed(KnowledgeSource source, String runId, IndexOperation operation, Instant startedAt,
                    Instant completedAt, String fingerprint, int failedDocuments, String failureReason);

    static IndexRunStatusStore noOp() {
        return new IndexRunStatusStore() {
            @Override
            public void markRunning(KnowledgeSource source, String runId, IndexOperation operation, Instant startedAt,
                                    String fingerprint) {
            }

            @Override
            public void markSucceeded(SourceIndexRunReport report, String runId, IndexOperation operation,
                                      Instant startedAt, Instant completedAt, String fingerprint) {
            }

            @Override
            public void markFailed(KnowledgeSource source, String runId, IndexOperation operation, Instant startedAt,
                                   Instant completedAt, String fingerprint, int failedDocuments,
                                   String failureReason) {
            }
        };
    }
}
