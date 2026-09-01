package com.healingplanet.ai.ingestion;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.domain.IndexStatus;
import com.healingplanet.ai.domain.KnowledgeSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IndexStatusServiceTest {

    @Test
    void shouldExposeFingerprintAndSourceLagAlertsWithoutStartingAnIndexRun() {
        Instant now = Instant.parse("2026-09-01T12:30:00Z");
        RagProperties properties = new RagProperties();
        properties.getIndexObservability().setSourceLagAlertThreshold(Duration.ofMinutes(10));
        IndexStatusRepository statusRepository = mock(IndexStatusRepository.class);
        SourceFreshnessRepository freshnessRepository = mock(SourceFreshnessRepository.class);
        IndexStatusRepository.PersistedStatus community = new IndexStatusRepository.PersistedStatus(
                KnowledgeSource.COMMUNITY, "run-1", "COMMUNITY", now.minus(Duration.ofMinutes(31)),
                now.minus(Duration.ofMinutes(30)), now.minus(Duration.ofMinutes(30)), "SUCCEEDED", "old", 1, 0,
                1, 0, 1, 0, 1, 1, 0, "");
        when(statusRepository.findAll()).thenReturn(Map.of(KnowledgeSource.COMMUNITY, community));
        when(statusRepository.embeddingStateStats(anyString())).thenReturn(Map.of(KnowledgeSource.COMMUNITY,
                new IndexStatusRepository.EmbeddingStateStats(KnowledgeSource.COMMUNITY, 2, 2)));
        when(freshnessRepository.findLag(any(), anyString())).thenAnswer(invocation ->
                invocation.getArgument(0) == KnowledgeSource.COMMUNITY
                        ? new SourceFreshnessRepository.SourceLag(true, 3, now.minus(Duration.ofMinutes(5)))
                        : SourceFreshnessRepository.SourceLag.unsupported());
        var registry = new SimpleMeterRegistry();
        IndexStatusService service = new IndexStatusService(statusRepository, freshnessRepository, properties,
                Clock.fixed(now, ZoneOffset.UTC), new IndexMetrics(registry));

        IndexStatus status = service.status();

        assertThat(status.currentFingerprint()).isNotBlank();
        assertThat(status.sources()).filteredOn(item -> item.source() == KnowledgeSource.COMMUNITY)
                .singleElement().satisfies(item -> {
                    assertThat(item.freshness()).isEqualTo(IndexStatus.Freshness.STALE);
                    assertThat(item.staleFingerprintFragments()).isEqualTo(2);
                    assertThat(item.staleSourceCount()).isEqualTo(3);
                    assertThat(item.sourceLagSeconds()).isEqualTo(1500);
                });
        assertThat(status.alerts()).extracting(IndexStatus.IndexAlert::reason)
                .contains(IndexStatus.AlertReason.STALE_FINGERPRINT, IndexStatus.AlertReason.SOURCE_LAG);
        assertThat(registry.get(IndexMetrics.STALE_GAUGE)
                .tags("source", "community", "kind", "fingerprint").gauge().value()).isEqualTo(2d);
        assertThat(registry.get(IndexMetrics.SOURCE_LAG_GAUGE)
                .tags("source", "community", "kind", "latest").gauge().value()).isEqualTo(1500d);
    }
}
