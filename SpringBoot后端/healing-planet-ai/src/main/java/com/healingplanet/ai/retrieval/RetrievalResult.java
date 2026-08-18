package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.EntityResolutionDiagnostics;
import com.healingplanet.ai.domain.Evidence;

import java.util.List;

public record RetrievalResult(List<Evidence> evidence, EntityResolutionDiagnostics entityResolution) {
    public RetrievalResult {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
}
