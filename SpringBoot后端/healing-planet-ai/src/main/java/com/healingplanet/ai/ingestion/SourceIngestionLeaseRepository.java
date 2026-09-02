package com.healingplanet.ai.ingestion;

import com.healingplanet.ai.domain.KnowledgeSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

/**
 * MySQL-backed source lease. Every mutation is conditional on the owner token so one instance can never release or
 * renew another instance's lease. Database time is deliberately used for expiry comparisons to avoid host clock skew.
 */
@Repository
public class SourceIngestionLeaseRepository {
    private final JdbcTemplate jdbcTemplate;

    public SourceIngestionLeaseRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean tryAcquire(KnowledgeSource source, String owner, Duration leaseDuration) {
        long leaseMicros = leaseMicros(leaseDuration);
        int inserted = jdbcTemplate.update("""
                insert ignore into rag_ingestion_lease (source, lease_owner, lease_until)
                values (?, ?, timestampadd(microsecond, ?, current_timestamp(3)))
                """, source.name(), owner, leaseMicros);
        if (inserted == 1) {
            return true;
        }
        return jdbcTemplate.update("""
                update rag_ingestion_lease
                set lease_owner = ?,
                    lease_until = timestampadd(microsecond, ?, current_timestamp(3)),
                    updated_at = current_timestamp(3)
                where source = ?
                  and (lease_owner = ? or lease_until <= current_timestamp(3))
                """, owner, leaseMicros, source.name(), owner) == 1;
    }

    public boolean renew(KnowledgeSource source, String owner, Duration leaseDuration) {
        return jdbcTemplate.update("""
                update rag_ingestion_lease
                set lease_until = timestampadd(microsecond, ?, current_timestamp(3)),
                    updated_at = current_timestamp(3)
                where source = ?
                  and lease_owner = ?
                  and lease_until > current_timestamp(3)
                """, leaseMicros(leaseDuration), source.name(), owner) == 1;
    }

    /** Checks ownership using database time without extending the lease. */
    public boolean isHeld(KnowledgeSource source, String owner) {
        Integer held = jdbcTemplate.queryForObject("""
                select exists(
                    select 1
                    from rag_ingestion_lease
                    where source = ?
                      and lease_owner = ?
                      and lease_until > current_timestamp(3)
                )
                """, Integer.class, source.name(), owner);
        return held != null && held == 1;
    }

    public void release(KnowledgeSource source, String owner) {
        jdbcTemplate.update("""
                update rag_ingestion_lease
                set lease_owner = '',
                    lease_until = current_timestamp(3),
                    updated_at = current_timestamp(3)
                where source = ? and lease_owner = ?
                """, source.name(), owner);
    }

    private long leaseMicros(Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException("app.rag.ingestion.source-lock-lease-duration 必须为正数");
        }
        try {
            return Math.max(1, value.toNanos() / 1_000);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("app.rag.ingestion.source-lock-lease-duration 超出范围", exception);
        }
    }
}
