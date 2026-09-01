package com.healingplanet.ai.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/** Per-source accounting for one explicit ingestion run. */
public record SourceIndexRunReport(
        KnowledgeSource source,
        int documentsSeen,
        int documentsUnchanged,
        int documentsEmbedded,
        int payloadUpdates,
        int sparseUpdates,
        int documentsDeleted,
        int fragmentsCreated,
        int logicalEvidencesCreated,
        int failedDocuments,
        Map<String, Integer> reembedReasons
) {
    public SourceIndexRunReport {
        reembedReasons = reembedReasons == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(reembedReasons));
    }
}
