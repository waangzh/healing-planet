package com.healingplanet.ai.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.domain.PlantState;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

@Component
@Profile("eval")
public class EvalPlantStateStub implements PlantStateGateway {
    private static final Long EVAL_USER_ID = 7L;
    private final Map<Long, PlantState> states;

    public EvalPlantStateStub(ObjectMapper objectMapper, RagProperties properties) {
        Path fixtures = properties.getEval().getFixtureDirectory();
        states = Map.of(
                102L, readFixture(objectMapper, fixtures, "green-pothos-dry.json"),
                103L, readFixture(objectMapper, fixtures, "green-pothos-wet.json"),
                104L, readFixture(objectMapper, fixtures, "green-pothos-stale.json")
        );
    }

    @Override
    public Optional<PlantState> get(Long plantInstanceId, Long userId) {
        if (!EVAL_USER_ID.equals(userId)) return Optional.empty();
        return Optional.ofNullable(states.get(plantInstanceId));
    }

    private PlantState readFixture(ObjectMapper objectMapper, Path directory, String filename) {
        try {
            return objectMapper.readValue(directory.resolve(filename).toFile(), PlantState.class);
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取评测 Plant State Fixture：" + directory.resolve(filename), exception);
        }
    }
}
