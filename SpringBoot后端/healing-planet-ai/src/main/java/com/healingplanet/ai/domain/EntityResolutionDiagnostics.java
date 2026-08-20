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
        String rejectionReason,
        List<AliasNormalization> aliasNormalizations
) {
    public EntityResolutionDiagnostics {
        canonicalPlantIds = canonicalPlantIds == null ? List.of() : List.copyOf(canonicalPlantIds);
        rejectionReason = rejectionReason == null ? "" : rejectionReason;
        aliasNormalizations = aliasNormalizations == null ? List.of() : List.copyOf(aliasNormalizations);
    }

    public EntityResolutionDiagnostics(String resolutionKind, String resolutionMethod, String canonicalPlantId,
                                       List<String> canonicalPlantIds, double top1Score, double top2Score,
                                       double scoreMargin, int candidateCount, String rejectionReason) {
        this(resolutionKind, resolutionMethod, canonicalPlantId, canonicalPlantIds, top1Score, top2Score,
                scoreMargin, candidateCount, rejectionReason, List.of());
    }

    public record AliasNormalization(String alias, String canonicalPlantId, String canonicalPlantName) { }
}
