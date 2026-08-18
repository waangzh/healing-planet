package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.Evidence;
import com.healingplanet.ai.domain.RagQuery;

import java.util.List;

public interface EvidenceRetriever {
    List<Evidence> retrieve(RagQuery query);

    default RetrievalResult retrieveWithDiagnostics(RagQuery query) {
        return new RetrievalResult(retrieve(query), null);
    }
}
