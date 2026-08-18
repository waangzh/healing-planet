package com.healingplanet.ai.retrieval;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetrievalMetricsTest {

    @Test
    void shouldRecordStageDurationAndCandidateCount() {
        var registry = new SimpleMeterRegistry();
        var metrics = new RetrievalMetrics(registry);

        String result = metrics.time("dense_search", "plant", () -> "ok");
        metrics.recordCandidates("dense", "plant", 3);

        assertThat(result).isEqualTo("ok");
        assertThat(registry.get(RetrievalMetrics.STAGE_TIMER)
                .tags("stage", "dense_search", "source", "plant", "status", "ok")
                .timer().count()).isEqualTo(1);
        assertThat(registry.get(RetrievalMetrics.CANDIDATE_SUMMARY)
                .tags("stage", "dense", "source", "plant")
                .summary().totalAmount()).isEqualTo(3);
    }

    @Test
    void shouldRecordFailedStage() {
        var registry = new SimpleMeterRegistry();
        var metrics = new RetrievalMetrics(registry);

        assertThatThrownBy(() -> metrics.time("rerank", "all", () -> {
            throw new IllegalStateException("failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(registry.get(RetrievalMetrics.STAGE_TIMER)
                .tags("stage", "rerank", "source", "all", "status", "error")
                .timer().count()).isEqualTo(1);
    }
}
