package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.EntityResolutionDiagnostics;
import com.healingplanet.ai.domain.Evidence;
import com.healingplanet.ai.domain.RetrievalTrace;

import java.util.List;

public record RetrievalResult(List<Evidence> evidence, EntityResolutionDiagnostics entityResolution,
                              RetrievalTrace retrievalTrace) {
    public RetrievalResult {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public RetrievalResult(List<Evidence> evidence, EntityResolutionDiagnostics entityResolution) {
        this(evidence, entityResolution, null);
    }
}
