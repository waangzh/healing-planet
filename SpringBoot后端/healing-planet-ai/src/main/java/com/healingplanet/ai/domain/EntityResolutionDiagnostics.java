package com.healingplanet.ai.domain;

import java.util.List;

public record EntityResolutionDiagnostics(
        String resolutionKind,
        String resolutionMethod,
        String canonicalPlantId,
        List<String> canonicalPlantIds,
        double top1Score,
        double top2Score,
        double scoreMargin,
        int candidateCount,
        String rejectionReason
) {
    public EntityResolutionDiagnostics {
        canonicalPlantIds = canonicalPlantIds == null ? List.of() : List.copyOf(canonicalPlantIds);
        rejectionReason = rejectionReason == null ? "" : rejectionReason;
    }
}
