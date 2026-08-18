package com.healingplanet.ai.domain;

import java.util.List;

public record RagResponse(String answer, List<Evidence> evidence,
                          EntityResolutionDiagnostics entityResolution) {
    public RagResponse(String answer, List<Evidence> evidence) {
        this(answer, evidence, null);
    }
}
