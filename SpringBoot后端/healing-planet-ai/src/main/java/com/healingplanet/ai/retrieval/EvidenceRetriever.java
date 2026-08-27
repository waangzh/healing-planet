package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.Evidence;
import java.util.List;

public interface EvidenceRetriever {
    List<Evidence> retrieve(RetrievalRequest request);

    default RetrievalResult retrieveWithDiagnostics(RetrievalRequest request) {
        return new RetrievalResult(retrieve(request), null);
    }
}
