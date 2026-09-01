package com.healingplanet.ai.ingestion;

import com.healingplanet.ai.domain.KnowledgeDocument;
import com.healingplanet.ai.domain.KnowledgeSource;

import java.util.List;

/** Updates vector-store metadata while retaining the already-computed embedding vector. */
public interface VectorPayloadUpdater {
    void overwritePayloads(KnowledgeSource source, List<KnowledgeDocument> documents);

    static VectorPayloadUpdater noOp() {
        return (source, documents) -> { };
    }
}
