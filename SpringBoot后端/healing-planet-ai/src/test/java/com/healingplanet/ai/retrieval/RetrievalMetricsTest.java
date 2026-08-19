package com.healingplanet.ai.retrieval;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

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

    @Test
    void shouldRecordReactiveStageWhenStreamTerminates() {
        var registry = new SimpleMeterRegistry();
        var metrics = new RetrievalMetrics(registry);

        assertThat(metrics.timeFlux("answer_generation", "llm", () -> Flux.just("a", "b"))
                .collectList().block()).containsExactly("a", "b");

        assertThat(registry.get(RetrievalMetrics.STAGE_TIMER)
                .tags("stage", "answer_generation", "source", "llm", "status", "ok")
                .timer().count()).isEqualTo(1);
    }

    @Test
    void shouldExposeAggregableHistogramToPrometheus() {
        var registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        var metrics = new RetrievalMetrics(registry);

        metrics.time("dense_search", "plant", () -> "ok");
        metrics.recordCandidates("dense", "plant", 3);

        assertThat(registry.scrape())
                .contains("healing_planet_rag_retrieval_stage_seconds_bucket")
                .contains("healing_planet_rag_retrieval_stage_seconds_count")
                .contains("healing_planet_rag_retrieval_candidates_count")
                .contains("healing_planet_rag_retrieval_candidates_sum")
                .containsPattern("healing_planet_rag_retrieval_stage_seconds_bucket\\{(?=[^}]*stage=\\\"dense_search\\\")(?=[^}]*source=\\\"plant\\\")(?=[^}]*status=\\\"ok\\\")[^}]*}");
    }
}
