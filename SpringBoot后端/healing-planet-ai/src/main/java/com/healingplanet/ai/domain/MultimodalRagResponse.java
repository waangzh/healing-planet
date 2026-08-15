package com.healingplanet.ai.domain;

import java.util.List;

public record MultimodalRagResponse(
        String answer,
        List<Evidence> evidence,
        String attachmentId,
        MultimodalRoute route,
        VisualObservation visualObservation,
        boolean stateContextUsed,
        long expiresInSeconds,
        String notice
) {
}
