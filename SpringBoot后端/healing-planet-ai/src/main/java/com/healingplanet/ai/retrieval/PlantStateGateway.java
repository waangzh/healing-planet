package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.PlantState;

import java.util.Optional;

public interface PlantStateGateway {
    Optional<PlantState> get(Long plantInstanceId, Long userId);
}
