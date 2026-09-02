package com.healingplanet.ai.ingestion;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.domain.KnowledgeSource;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Acquires a database lease before a source scan or incremental write. The heartbeat keeps a long full scan owned;
 * expiry lets another instance recover after a crash without relying on an in-process monitor.
 */
@Component
public class JdbcSourceIngestionLock implements SourceIngestionLock {
    private static final Logger log = LoggerFactory.getLogger(JdbcSourceIngestionLock.class);

    private final SourceIngestionLeaseRepository repository;
    private final RagProperties properties;
    private final ScheduledExecutorService heartbeatExecutor;

    public JdbcSourceIngestionLock(SourceIngestionLeaseRepository repository, RagProperties properties) {
        this(repository, properties, Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "rag-source-ingestion-lease");
            thread.setDaemon(true);
            return thread;
        }));
    }

    JdbcSourceIngestionLock(SourceIngestionLeaseRepository repository, RagProperties properties,
                             ScheduledExecutorService heartbeatExecutor) {
        this.repository = repository;
        this.properties = properties;
        this.heartbeatExecutor = heartbeatExecutor;
    }

    @Override
    public void execute(KnowledgeSource source, LeaseAction action) {
        Duration leaseDuration = properties.getIngestion().getSourceLockLeaseDuration();
        String owner = UUID.randomUUID().toString();
        acquire(source, owner, leaseDuration);
        AtomicReference<RuntimeException> leaseFailure = new AtomicReference<>();
        AtomicBoolean closing = new AtomicBoolean();
        Object renewalMonitor = new Object();
        ScheduledFuture<?> heartbeat = null;
        RuntimeException actionFailure = null;
        try {
            heartbeat = heartbeatExecutor.scheduleAtFixedRate(
                    () -> renew(source, owner, leaseDuration, leaseFailure, closing, renewalMonitor),
                    heartbeatPeriodMillis(leaseDuration), heartbeatPeriodMillis(leaseDuration), TimeUnit.MILLISECONDS);
            action.run(() -> assertStillHeld(source, owner, leaseFailure));
            // The final database-time check makes the caller's success transition depend on the completed lease too.
            assertStillHeld(source, owner, leaseFailure);
        } catch (RuntimeException exception) {
            actionFailure = exception;
        } finally {
            closing.set(true);
            if (heartbeat != null) {
                heartbeat.cancel(false);
            }
            synchronized (renewalMonitor) {
                repository.release(source, owner);
            }
        }
        if (actionFailure != null) {
            throw actionFailure;
        }
        RuntimeException failure = leaseFailure.get();
        if (failure != null) {
            throw failure;
        }
    }

    private void acquire(KnowledgeSource source, String owner, Duration leaseDuration) {
        Duration timeout = properties.getIngestion().getSourceLockAcquireTimeout();
        Duration retryDelay = properties.getIngestion().getSourceLockRetryDelay();
        if (timeout == null || timeout.isNegative() || retryDelay == null || retryDelay.isNegative()
                || retryDelay.isZero()) {
            throw new IllegalStateException("app.rag.ingestion 的 source lock 超时和重试间隔必须为有效正值");
        }
        long deadline = System.nanoTime() + timeout.toNanos();
        do {
            if (repository.tryAcquire(source, owner, leaseDuration)) {
                return;
            }
            sleep(retryDelay);
        } while (System.nanoTime() < deadline);
        throw new SourceIngestionLeaseException("等待 " + source + " 索引租约超时");
    }

    private void renew(KnowledgeSource source, String owner, Duration leaseDuration,
                       AtomicReference<RuntimeException> leaseFailure, AtomicBoolean closing,
                       Object renewalMonitor) {
        if (closing.get() || leaseFailure.get() != null) {
            return;
        }
        synchronized (renewalMonitor) {
            if (closing.get() || leaseFailure.get() != null) {
                return;
            }
            try {
                if (!repository.renew(source, owner, leaseDuration)) {
                    leaseFailure.compareAndSet(null, new SourceIngestionLeaseException(source + " 索引租约已丢失"));
                }
            } catch (RuntimeException exception) {
                log.warn("续约 {} 索引租约失败", source, exception);
                leaseFailure.compareAndSet(null,
                        new SourceIngestionLeaseException(source + " 索引租约续约失败", exception));
            }
        }
    }

    private void assertStillHeld(KnowledgeSource source, String owner,
                                 AtomicReference<RuntimeException> leaseFailure) {
        RuntimeException failure = leaseFailure.get();
        if (failure != null) {
            throw failure;
        }
        try {
            if (!repository.isHeld(source, owner)) {
                SourceIngestionLeaseException lost = new SourceIngestionLeaseException(source + " 索引租约已丢失");
                leaseFailure.compareAndSet(null, lost);
                throw leaseFailure.get();
            }
        } catch (SourceIngestionLeaseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            SourceIngestionLeaseException failed = new SourceIngestionLeaseException(
                    source + " 索引租约状态检查失败", exception);
            leaseFailure.compareAndSet(null, failed);
            throw leaseFailure.get();
        }
    }

    private long heartbeatPeriodMillis(Duration leaseDuration) {
        if (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalStateException("app.rag.ingestion.source-lock-lease-duration 必须为正数");
        }
        return Math.max(1, leaseDuration.toMillis() / 3);
    }

    private void sleep(Duration retryDelay) {
        try {
            Thread.sleep(Math.max(1, retryDelay.toMillis()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待索引租约时被中断", exception);
        }
    }

    @PreDestroy
    void shutdown() {
        heartbeatExecutor.shutdownNow();
    }
}
