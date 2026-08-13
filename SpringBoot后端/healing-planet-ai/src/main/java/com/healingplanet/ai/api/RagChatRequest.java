package com.healingplanet.ai.api;

import com.healingplanet.ai.domain.QueryIntent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RagChatRequest(
        Long userId,
        Long plantInstanceId,
        String canonicalPlantId,
        QueryIntent intent,
        @NotBlank @Size(max = 2000) String query
) {
}
