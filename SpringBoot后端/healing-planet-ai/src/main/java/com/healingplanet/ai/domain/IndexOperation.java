package com.healingplanet.ai.domain;

/** Internal ingestion operation names kept low-cardinality for status and metrics. */
public enum IndexOperation {
    FULL,
    PLANTS,
    COMMUNITY,
    DISEASES,
    POST_UPSERT,
    POST_DELETE,
    DISEASE_UPSERT
}
