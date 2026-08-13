package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.PlantState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

@Component
public class PlantStateClient {
    private static final Logger log = LoggerFactory.getLogger(PlantStateClient.class);
    private final RestClient restClient;

    public PlantStateClient(@Qualifier("plantStateRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public Optional<PlantState> get(Long plantInstanceId, Long userId) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri(builder -> builder.path("/internal/plant-state/{id}")
                            .queryParam("userId", userId).build(plantInstanceId))
                    .retrieve().body(PlantState.class));
        } catch (RestClientException exception) {
            log.warn("获取植物状态失败，plantInstanceId={}: {}", plantInstanceId, exception.getMessage());
            return Optional.empty();
        }
    }
}
