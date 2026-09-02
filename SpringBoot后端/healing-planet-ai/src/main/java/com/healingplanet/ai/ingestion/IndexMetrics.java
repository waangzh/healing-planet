package com.healingplanet.ai.ingestion;

import com.healingplanet.ai.domain.IndexRunReport;
import com.healingplanet.ai.domain.IndexStatus;
import com.healingplanet.ai.domain.SourceIndexRunReport;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Low-cardinality ingestion telemetry. Freshness gauges are refreshed by a read-only scheduled probe. */
@Component
public class IndexMetrics {
    static final String RUN_TIMER = "healing.planet.rag.index.run";
    static final String DOCUMENT_COUNTER = "healing.planet.rag.index.documents";
    static final String REEMBED_COUNTER = "healing.planet.rag.index.reembed";
    static final String STALE_GAUGE = "healing.planet.rag.index.stale_fragments";
    static final String SOURCE_LAG_GAUGE = "healing.planet.rag.index.source_lag_seconds";

    private final MeterRegistry registry;
    private final Map<String, AtomicLong> freshnessValues = new ConcurrentHashMap<>();

    public IndexMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordRun(IndexRunReport report) {
        if (registry == null) return;
        Duration duration = Duration.between(report.startedAt(), report.completedAt());
        Timer.builder(RUN_TIMER)
                .description("RAG ingestion operation duration")
                .tag("operation", report.operation().name().toLowerCase())
                .tag("status", report.status().name().toLowerCase())
                .publishPercentileHistogram()
                .register(registry)
                .record(duration.isNegative() ? Duration.ZERO : duration);
        for (SourceIndexRunReport source : report.sources()) {
            Tags tags = Tags.of("operation", report.operation().name().toLowerCase(),
                    "source", source.source().name().toLowerCase());
            increment(tags, "unchanged", source.documentsUnchanged());
            increment(tags, "embedded", source.documentsEmbedded());
            increment(tags, "payload_updated", source.payloadUpdates());
            increment(tags, "sparse_updated", source.sparseUpdates());
            increment(tags, "deleted", source.documentsDeleted());
            increment(tags, "failed", source.failedDocuments());
            increment(tags, "fragments_created", source.fragmentsCreated());
            increment(tags, "logical_evidences_created", source.logicalEvidencesCreated());
            source.reembedReasons().forEach((reason, count) -> Counter.builder(REEMBED_COUNTER)
                    .description("RAG re-embedding documents by reason")
                    .tags(tags.and("reason", reason))
                    .register(registry)
                    .increment(count));
        }
    }

    public void recordFreshness(IndexStatus status) {
        if (registry == null) return;
        for (IndexStatus.SourceIndexStatus source : status.sources()) {
            updateGauge(STALE_GAUGE, source.source().name().toLowerCase(), "fingerprint",
                    source.staleFingerprintFragments());
            updateGauge(STALE_GAUGE, source.source().name().toLowerCase(), "source_lag", source.staleSourceCount());
            updateGauge(SOURCE_LAG_GAUGE, source.source().name().toLowerCase(), "stale_age",
                    source.sourceLagSeconds() == null ? 0 : source.sourceLagSeconds());
        }
    }

    private void increment(Tags tags, String outcome, long count) {
        if (count <= 0) return;
        Counter.builder(DOCUMENT_COUNTER)
                .description("RAG ingestion document outcomes")
                .tags(tags.and("outcome", outcome))
                .register(registry)
                .increment(count);
    }

    private void updateGauge(String name, String source, String kind, long value) {
        String key = name + ':' + source + ':' + kind;
        AtomicLong holder = freshnessValues.computeIfAbsent(key, ignored -> {
            AtomicLong created = new AtomicLong();
            Gauge.builder(name, created, AtomicLong::get)
                    .description("RAG index freshness state")
                    .tags("source", source, "kind", kind)
                    .register(registry);
            return created;
        });
        holder.set(value);
    }

    static IndexMetrics noOp() {
        return new IndexMetrics(null);
    }
}
