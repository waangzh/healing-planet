package com.healingplanet.ai.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.domain.PlantState;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@Profile("eval")
public class EvalPlantStateStub implements PlantStateGateway {
    private static final Long EVAL_USER_ID = 7L;
    private final Map<Long, PlantState> states;

    public EvalPlantStateStub(ObjectMapper objectMapper, RagProperties properties) {
        Path fixtures = properties.getEval().getFixtureDirectory();
        states = loadFixtures(objectMapper, fixtures);
    }

    @Override
    public Optional<PlantState> get(Long plantInstanceId, Long userId) {
        if (!EVAL_USER_ID.equals(userId)) return Optional.empty();
        return Optional.ofNullable(states.get(plantInstanceId));
    }

    private Map<Long, PlantState> loadFixtures(ObjectMapper objectMapper, Path directory) {
        try (var files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .filter(path -> !"eval-clock.json".equals(path.getFileName().toString()))
                    .filter(path -> isStateFixture(objectMapper, path))
                    .map(path -> readFixture(objectMapper, path))
                    .filter(state -> state.plantInstanceId() != null && state.current() != null)
                    .collect(Collectors.toMap(PlantState::plantInstanceId, state -> state,
                            (left, right) -> { throw new IllegalStateException("评测 Fixture 的 plantInstanceId 重复"); },
                            java.util.LinkedHashMap::new));
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取评测 Plant State Fixture 目录：" + directory, exception);
        }
    }

    private PlantState readFixture(ObjectMapper objectMapper, Path path) {
        try {
            return objectMapper.readValue(path.toFile(), PlantState.class);
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取评测 Plant State Fixture：" + path, exception);
        }
    }

    private boolean isStateFixture(ObjectMapper objectMapper, Path path) {
        try {
            return objectMapper.readTree(path.toFile()).hasNonNull("current");
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取评测 Plant State Fixture：" + path, exception);
        }
    }
}
