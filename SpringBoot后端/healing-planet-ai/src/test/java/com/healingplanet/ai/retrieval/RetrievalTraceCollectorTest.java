package com.healingplanet.ai.retrieval;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetrievalTraceCollectorTest {

    @Test
    void shouldRecordFailedStageBeforeRethrowing() {
        RetrievalTraceCollector collector = new RetrievalTraceCollector(true);

        assertThatThrownBy(() -> collector.time("rerank", "all", "all", () -> {
            throw new IllegalStateException("reranker unavailable");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(collector.stages()).singleElement().satisfies(stage -> {
            assertThat(stage.stage()).isEqualTo("rerank");
            assertThat(stage.status()).isEqualTo("error");
            assertThat(stage.errorType()).isEqualTo(IllegalStateException.class.getName());
            assertThat(stage.errorMessage()).isEqualTo("reranker unavailable");
            assertThat(stage.durationMs()).isGreaterThanOrEqualTo(0);
        });
    }
}
