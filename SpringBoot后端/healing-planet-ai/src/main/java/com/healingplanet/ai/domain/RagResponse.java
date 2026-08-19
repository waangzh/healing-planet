package com.healingplanet.ai.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

public record RagResponse(String answer, List<Evidence> evidence,
                          EntityResolutionDiagnostics entityResolution,
                          @JsonInclude(JsonInclude.Include.NON_NULL) RetrievalTrace retrievalTrace) {
    public RagResponse(String answer, List<Evidence> evidence) {
        this(answer, evidence, null, null);
    }

    public RagResponse(String answer, List<Evidence> evidence, EntityResolutionDiagnostics entityResolution) {
        this(answer, evidence, entityResolution, null);
    }
}
