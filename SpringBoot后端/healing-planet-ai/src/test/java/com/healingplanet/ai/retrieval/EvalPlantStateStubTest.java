package com.healingplanet.ai.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healingplanet.ai.config.RagProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EvalPlantStateStubTest {
    @Test
    void shouldLoadAllStateFixturesOnlyForTheEvalUser() {
        var properties = new RagProperties();
        properties.getEval().setFixtureDirectory(Path.of("..", "..", "rag-eval", "fixtures"));
        var stub = new EvalPlantStateStub(new ObjectMapper().findAndRegisterModules(), properties);

        assertThat(stub.get(102L, 7L)).hasValueSatisfying(state ->
                assertThat(state.current().soilMoisture()).isEqualTo(20d));
        assertThat(stub.get(104L, 7L)).hasValueSatisfying(state ->
                assertThat(state.observedAt()).hasToString("2026-08-17T09:29"));
        assertThat(stub.get(105L, 7L)).hasValueSatisfying(state ->
                assertThat(state.current().soilMoisture()).isEqualTo(30d));
        assertThat(stub.get(999L, 7L)).isEmpty();
        assertThat(stub.get(102L, 8L)).isEmpty();
    }
}
