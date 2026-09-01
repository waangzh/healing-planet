package com.healingplanet.ai.ingestion;

import com.healingplanet.ai.domain.KnowledgeSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Read-only lag probes for sources with authoritative modification timestamps. Plant catalog tables do not expose
 * a stable update timestamp in the current schema, so their source lag intentionally remains UNKNOWN rather than
 * being guessed from a scan time.
 */
@Repository
public class SourceFreshnessRepository {
    private final JdbcTemplate jdbcTemplate;

    public SourceFreshnessRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public SourceLag findLag(KnowledgeSource source, String currentFingerprint) {
        return switch (source) {
            case COMMUNITY -> communityLag(currentFingerprint);
            case DISEASE -> diseaseLag(currentFingerprint);
            case PLANT, PLANT_ENTITY -> SourceLag.unsupported();
        };
    }

    private SourceLag communityLag(String currentFingerprint) {
        return jdbcTemplate.queryForObject("""
                select count(*) stale_sources, max(coalesce(p.modify_time, p.create_time)) latest_stale_source_updated_at
                from post p
                where (p.status = 1 or p.status is null)
                  and not exists (
                      select 1
                      from rag_embedding_state s
                      where s.source = 'COMMUNITY'
                        and s.source_id = cast(p.id as char)
                        and s.index_fingerprint = ?
                        and s.indexed_at >= coalesce(p.modify_time, p.create_time)
                  )
                """, (rs, rowNum) -> new SourceLag(true, rs.getLong("stale_sources"),
                instant(rs.getTimestamp("latest_stale_source_updated_at"))), currentFingerprint);
    }

    private SourceLag diseaseLag(String currentFingerprint) {
        return jdbcTemplate.queryForObject("""
                select count(*) stale_sources, max(coalesce(d.updated_at, d.created_at)) latest_stale_source_updated_at
                from plant_disease_knowledge d
                where not exists (
                    select 1
                    from rag_embedding_state s
                    where s.source = 'DISEASE'
                      and s.source_id = cast(d.id as char)
                      and s.index_fingerprint = ?
                      and s.indexed_at >= coalesce(d.updated_at, d.created_at)
                )
                """, (rs, rowNum) -> new SourceLag(true, rs.getLong("stale_sources"),
                instant(rs.getTimestamp("latest_stale_source_updated_at"))), currentFingerprint);
    }

    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    public record SourceLag(boolean supported, long staleSourceCount, Instant latestStaleSourceUpdatedAt) {
        public static SourceLag unsupported() {
            return new SourceLag(false, 0, null);
        }
    }
}
