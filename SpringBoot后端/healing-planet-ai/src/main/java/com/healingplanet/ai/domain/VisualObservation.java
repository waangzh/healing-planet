package com.healingplanet.ai.domain;

public record VisualObservation(
        MultimodalRoute recommendedRoute,
        String imageKind,
        String plantName,
        String summary,
        String colorChanges,
        String lesionShapeAndDistribution,
        String leafEdgeAndVein,
        String visiblePests,
        String recognizedText,
        String suspectedIssue,
        String imageQuality,
        String uncertainty,
        String searchQuery
) {
    public VisualObservation {
        recommendedRoute = recommendedRoute == null ? MultimodalRoute.GENERAL_VISION : recommendedRoute;
    }
}
