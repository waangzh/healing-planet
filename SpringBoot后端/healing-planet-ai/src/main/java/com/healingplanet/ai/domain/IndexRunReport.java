package com.healingplanet.ai.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Detailed report returned by an internal indexing operation. Counts are fragment-level unless their name says
 * logical evidence explicitly. The four summary fields retain the former internal IndexReport JSON contract.
 */
public record IndexRunReport(
        String runId,
        IndexOperation operation,
        Instant startedAt,
        Instant completedAt,
        Status status,
        int plantDocuments,
        int communityDocuments,
        int diseaseDocuments,
        int deletedDocuments,
        int documentsSeen,
        int documentsUnchanged,
        int documentsEmbedded,
        int payloadUpdates,
        int sparseUpdates,
        int fragmentsCreated,
        int logicalEvidencesCreated,
        int failedDocuments,
        Map<String, Integer> reembedReasons,
        List<SourceIndexRunReport> sources,
        String failureReason
) {
    public IndexRunReport {
        reembedReasons = reembedReasons == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(reembedReasons));
        sources = sources == null ? List.of() : List.copyOf(sources);
        failureReason = failureReason == null ? "" : failureReason;
    }

    public IndexReport summary() {
        return new IndexReport(plantDocuments, communityDocuments, diseaseDocuments, deletedDocuments);
    }

    public enum Status {
        SUCCEEDED,
        FAILED
    }
}
