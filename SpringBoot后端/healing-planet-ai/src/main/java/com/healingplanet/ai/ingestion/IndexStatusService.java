package com.healingplanet.ai.ingestion;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.domain.IndexStatus;
import com.healingplanet.ai.domain.KnowledgeSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Builds a read-only cross-instance freshness view; it never triggers an indexing scan or repair. */
@Service
public class IndexStatusService {
    private final IndexStatusRepository statusRepository;
    private final SourceFreshnessRepository sourceFreshnessRepository;
    private final RagProperties properties;
    private final Clock clock;
    private final IndexMetrics metrics;

    public IndexStatusService(IndexStatusRepository statusRepository,
                              SourceFreshnessRepository sourceFreshnessRepository,
                              RagProperties properties,
                              @Qualifier("ragClock") Clock clock,
                              IndexMetrics metrics) {
        this.statusRepository = statusRepository;
        this.sourceFreshnessRepository = sourceFreshnessRepository;
        this.properties = properties;
        this.clock = clock;
        this.metrics = metrics;
    }

    public IndexStatus status() {
        Instant checkedAt = clock.instant();
        String fingerprint = fingerprint().value();
        Map<KnowledgeSource, IndexStatusRepository.PersistedStatus> persisted = statusRepository.findAll();
        Map<KnowledgeSource, IndexStatusRepository.EmbeddingStateStats> stateStats =
                statusRepository.embeddingStateStats(fingerprint);
        List<IndexStatus.SourceIndexStatus> sources = new ArrayList<>();
        List<IndexStatus.IndexAlert> alerts = new ArrayList<>();
        for (KnowledgeSource source : KnowledgeSource.values()) {
            IndexStatusRepository.PersistedStatus run = persisted.get(source);
            IndexStatusRepository.EmbeddingStateStats state = stateStats.get(source);
            SourceFreshnessRepository.SourceLag lag = sourceFreshnessRepository.findLag(source, fingerprint);
            long indexedFragments = state == null ? 0 : state.indexedFragments();
            long staleFingerprintFragments = state == null ? 0 : state.staleFingerprintFragments();
            Long lagSeconds = staleAgeSeconds(lag, checkedAt);
            IndexStatus.Freshness freshness = freshness(run, indexedFragments, staleFingerprintFragments, lag);
            String lastError = run == null ? "" : run.lastError();
            sources.add(new IndexStatus.SourceIndexStatus(source, freshness,
                    run == null ? null : run.lastAttemptStartedAt(),
                    run == null ? null : run.lastSuccessfulIndexAt(),
                    run == null ? "" : run.lastRunStatus(),
                    run == null ? "" : run.lastIndexFingerprint(), indexedFragments, staleFingerprintFragments,
                    lag.supported(), lag.staleSourceCount(), lag.oldestStaleAt(),
                    lag.latestStaleSourceUpdatedAt(), lagSeconds, lastError));
            addAlerts(alerts, source, run, staleFingerprintFragments, lag, lagSeconds);
        }
        IndexStatus status = new IndexStatus(checkedAt, fingerprint, sources, alerts);
        metrics.recordFreshness(status);
        return status;
    }

    private void addAlerts(List<IndexStatus.IndexAlert> alerts, KnowledgeSource source,
                           IndexStatusRepository.PersistedStatus run, long staleFingerprintFragments,
                           SourceFreshnessRepository.SourceLag lag, Long lagSeconds) {
        if (run == null || run.lastSuccessfulIndexAt() == null) {
            alerts.add(new IndexStatus.IndexAlert(source, IndexStatus.AlertReason.NOT_INDEXED,
                    "尚未记录成功索引"));
        }
        if (run != null && "FAILED".equals(run.lastRunStatus())) {
            alerts.add(new IndexStatus.IndexAlert(source, IndexStatus.AlertReason.LAST_RUN_FAILED,
                    "最近一次索引运行失败"));
        }
        if (staleFingerprintFragments > 0) {
            alerts.add(new IndexStatus.IndexAlert(source, IndexStatus.AlertReason.STALE_FINGERPRINT,
                    "存在 " + staleFingerprintFragments + " 个 fragment 的索引指纹落后于当前配置"));
        }
        Duration threshold = properties.getIndexObservability().getSourceLagAlertThreshold();
        if (lag.staleSourceCount() > 0 && lagSeconds != null && lagSeconds >= threshold.toSeconds()) {
            alerts.add(new IndexStatus.IndexAlert(source, IndexStatus.AlertReason.SOURCE_LAG,
                    "存在 " + lag.staleSourceCount() + " 个 source 超过新鲜度阈值"));
        }
    }

    private IndexStatus.Freshness freshness(IndexStatusRepository.PersistedStatus run, long indexedFragments,
                                             long staleFingerprintFragments,
                                             SourceFreshnessRepository.SourceLag lag) {
        if (run != null && "FAILED".equals(run.lastRunStatus())) return IndexStatus.Freshness.LAST_RUN_FAILED;
        if (run == null || run.lastSuccessfulIndexAt() == null) return IndexStatus.Freshness.NOT_INDEXED;
        if (staleFingerprintFragments > 0 || lag.staleSourceCount() > 0) return IndexStatus.Freshness.STALE;
        if (!lag.supported() && indexedFragments == 0) return IndexStatus.Freshness.UNKNOWN;
        return IndexStatus.Freshness.FRESH;
    }

    /**
     * This is a stale age, not a source-watermark delta: a one-minute-old unprocessed change must keep growing
     * while the projection is stalled, even when no newer source change arrives.
     */
    private Long staleAgeSeconds(SourceFreshnessRepository.SourceLag lag, Instant checkedAt) {
        if (!lag.supported() || lag.oldestStaleAt() == null) return null;
        return Math.max(0, Duration.between(lag.oldestStaleAt(), checkedAt).toSeconds());
    }

    private IndexFingerprint fingerprint() {
        var ingestion = properties.getIngestion();
        return new IndexFingerprint(ingestion.getEmbeddingModelVersion(), ingestion.getEmbeddingContentVersion(),
                ingestion.getChunkSchemaVersion());
    }
}
