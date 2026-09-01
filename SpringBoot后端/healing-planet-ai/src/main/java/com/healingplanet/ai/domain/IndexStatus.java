package com.healingplanet.ai.domain;

import java.time.Instant;
import java.util.List;

/** Persistent indexing state plus a read-time freshness assessment. */
public record IndexStatus(
        Instant checkedAt,
        String currentFingerprint,
        List<SourceIndexStatus> sources,
        List<IndexAlert> alerts
) {
    public IndexStatus {
        sources = sources == null ? List.of() : List.copyOf(sources);
        alerts = alerts == null ? List.of() : List.copyOf(alerts);
    }

    public record SourceIndexStatus(
            KnowledgeSource source,
            Freshness freshness,
            Instant lastAttemptAt,
            Instant lastSuccessfulIndexAt,
            String lastRunStatus,
            String lastIndexedFingerprint,
            long indexedFragments,
            long staleFingerprintFragments,
            boolean sourceLagSupported,
            long staleSourceCount,
            Instant latestStaleSourceUpdatedAt,
            Long sourceLagSeconds,
            String lastError
    ) {
        public SourceIndexStatus {
            lastRunStatus = lastRunStatus == null ? "" : lastRunStatus;
            lastIndexedFingerprint = lastIndexedFingerprint == null ? "" : lastIndexedFingerprint;
            lastError = lastError == null ? "" : lastError;
        }
    }

    public record IndexAlert(KnowledgeSource source, AlertReason reason, String message) {
    }

    public enum Freshness {
        FRESH,
        STALE,
        NOT_INDEXED,
        LAST_RUN_FAILED,
        UNKNOWN
    }

    public enum AlertReason {
        STALE_FINGERPRINT,
        SOURCE_LAG,
        LAST_RUN_FAILED,
        NOT_INDEXED
    }
}
