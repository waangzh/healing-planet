package com.healingplanet.ai.ingestion;

import com.healingplanet.ai.domain.IndexOperation;
import com.healingplanet.ai.domain.IndexRunReport;
import com.healingplanet.ai.domain.KnowledgeSource;
import com.healingplanet.ai.domain.SourceIndexRunReport;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IndexMetricsTest {

    @Test
    void shouldRecordLowCardinalityIndexRunMetrics() {
        var registry = new SimpleMeterRegistry();
        var metrics = new IndexMetrics(registry);
        Instant startedAt = Instant.parse("2026-09-01T12:00:00Z");
        var source = new SourceIndexRunReport(KnowledgeSource.COMMUNITY, 4, 1, 2, 1,
                3, 0, 1, 1, 0, Map.of("content_changed", 2));
        var report = new IndexRunReport("run-1", IndexOperation.COMMUNITY, startedAt, startedAt.plusSeconds(2),
                IndexRunReport.Status.SUCCEEDED, 0, 4, 0, 0, 4, 1, 2, 1, 3, 1, 1, 0,
                Map.of("content_changed", 2), List.of(source), "");

        metrics.recordRun(report);

        assertThat(registry.get(IndexMetrics.RUN_TIMER).tags("operation", "community", "status", "succeeded")
                .timer().count()).isEqualTo(1);
        assertThat(registry.get(IndexMetrics.DOCUMENT_COUNTER)
                .tags("operation", "community", "source", "community", "outcome", "embedded")
                .counter().count()).isEqualTo(2d);
        assertThat(registry.get(IndexMetrics.REEMBED_COUNTER)
                .tags("operation", "community", "source", "community", "reason", "content_changed")
                .counter().count()).isEqualTo(2d);
    }
}
