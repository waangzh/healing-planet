package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.config.RagRuntimeSnapshot;
import com.healingplanet.ai.domain.Evidence;
import java.util.List;

public interface EvidenceRetriever {
    List<Evidence> retrieve(RetrievalRequest request);

    default RetrievalResult retrieveWithDiagnostics(RetrievalRequest request) {
        return new RetrievalResult(retrieve(request), null);
    }

    /** Uses the request-scoped immutable runtime snapshot when the retriever has runtime-tunable stages. */
    RetrievalResult retrieveWithDiagnostics(RetrievalRequest request, RagRuntimeSnapshot runtimeSnapshot);
}
