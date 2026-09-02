package com.healingplanet.ai.ingestion;

import com.healingplanet.ai.domain.KnowledgeSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Read-only freshness probes. Community is an asynchronous outbox projection, so its freshness is defined by
 * undelivered index events. Disease knowledge has a source version timestamp, which is compared with the version
 * persisted alongside every indexed fragment. Plant catalog tables do not expose a stable source version yet.
 */
@Repository
public class SourceFreshnessRepository {
    private final JdbcTemplate jdbcTemplate;

    public SourceFreshnessRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public SourceLag findLag(KnowledgeSource source, String currentFingerprint) {
        return switch (source) {
            case COMMUNITY -> communityLag();
            case DISEASE -> diseaseLag(currentFingerprint);
            case PLANT, PLANT_ENTITY -> SourceLag.unsupported();
        };
    }

    private SourceLag communityLag() {
        return jdbcTemplate.queryForObject("""
                select count(*) stale_sources,
                       min(occurred_at) oldest_stale_at,
                       max(occurred_at) latest_stale_source_updated_at
                from post_index_outbox
                where event_type in ('POST_UPSERT', 'POST_DELETE')
                  and delivered_at is null
                """, (rs, rowNum) -> new SourceLag(true, rs.getLong("stale_sources"),
                instant(rs.getTimestamp("oldest_stale_at")),
                instant(rs.getTimestamp("latest_stale_source_updated_at"))));
    }

    private SourceLag diseaseLag(String currentFingerprint) {
        return jdbcTemplate.queryForObject("""
                select count(*) stale_sources,
                       min(coalesce(d.updated_at, d.created_at)) oldest_stale_at,
                       max(coalesce(d.updated_at, d.created_at)) latest_stale_source_updated_at
                from plant_disease_knowledge d
                where coalesce((
                    select min(s.source_updated_at)
                    from rag_embedding_state s
                    where s.source = 'DISEASE'
                      and s.source_id = cast(d.id as char)
                      and s.index_fingerprint = ?
                ), cast('1000-01-01 00:00:00.000' as datetime)) < coalesce(d.updated_at, d.created_at)
                """, (rs, rowNum) -> new SourceLag(true, rs.getLong("stale_sources"),
                instant(rs.getTimestamp("oldest_stale_at")),
                instant(rs.getTimestamp("latest_stale_source_updated_at"))), currentFingerprint);
    }

    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    public record SourceLag(boolean supported, long staleSourceCount, Instant oldestStaleAt,
                            Instant latestStaleSourceUpdatedAt) {
        /** Keeps test fixtures and callers compiled against the earlier single-stale-time representation. */
        public SourceLag(boolean supported, long staleSourceCount, Instant staleAt) {
            this(supported, staleSourceCount, staleAt, staleAt);
        }

        public static SourceLag unsupported() {
            return new SourceLag(false, 0, null, null);
        }
    }
}
