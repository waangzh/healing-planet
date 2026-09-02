package com.healingplanet.ai.ingestion;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.domain.KnowledgeSource;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcSourceIngestionLockTest {

    @Test
    void shouldAcquireHeartBeatAndReleaseTheSourceLeaseAroundAnIndexOperation() {
        SourceIngestionLeaseRepository repository = mock(SourceIngestionLeaseRepository.class);
        when(repository.tryAcquire(eq(KnowledgeSource.COMMUNITY), anyString(), eq(Duration.ofMinutes(5))))
                .thenReturn(true);
        when(repository.isHeld(eq(KnowledgeSource.COMMUNITY), anyString())).thenReturn(true);
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future).when(executor)
                .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS));
        AtomicBoolean executed = new AtomicBoolean();

        new JdbcSourceIngestionLock(repository, new RagProperties(), executor)
                .execute(KnowledgeSource.COMMUNITY, lease -> {
                    lease.assertStillHeld();
                    executed.set(true);
                });

        assertThat(executed).isTrue();
        verify(executor).scheduleAtFixedRate(any(Runnable.class), eq(100_000L), eq(100_000L),
                eq(TimeUnit.MILLISECONDS));
        verify(repository).release(eq(KnowledgeSource.COMMUNITY), anyString());
        verify(repository).isHeld(eq(KnowledgeSource.COMMUNITY), anyString());
        verify(future).cancel(false);
    }

    @Test
    void shouldStopTheNextBatchWhenTheDatabaseLeaseHasExpiredBeforeHeartbeatRuns() {
        SourceIngestionLeaseRepository repository = mock(SourceIngestionLeaseRepository.class);
        when(repository.tryAcquire(eq(KnowledgeSource.COMMUNITY), anyString(), eq(Duration.ofMinutes(5))))
                .thenReturn(true);
        when(repository.isHeld(eq(KnowledgeSource.COMMUNITY), anyString())).thenReturn(false);
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future).when(executor)
                .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS));
        AtomicBoolean nextBatchStarted = new AtomicBoolean();

        assertThatThrownBy(() -> new JdbcSourceIngestionLock(repository, new RagProperties(), executor)
                .execute(KnowledgeSource.COMMUNITY, lease -> {
                    lease.assertStillHeld();
                    nextBatchStarted.set(true);
                }))
                .isInstanceOf(SourceIngestionLeaseException.class)
                .hasMessageContaining("COMMUNITY 索引租约已丢失");

        assertThat(nextBatchStarted).isFalse();
        verify(repository).release(eq(KnowledgeSource.COMMUNITY), anyString());
    }

    @Test
    void shouldStopTheNextBatchWhenHeartbeatLosesTheLease() {
        SourceIngestionLeaseRepository repository = mock(SourceIngestionLeaseRepository.class);
        when(repository.tryAcquire(eq(KnowledgeSource.COMMUNITY), anyString(), eq(Duration.ofMinutes(5))))
                .thenReturn(true);
        when(repository.renew(eq(KnowledgeSource.COMMUNITY), anyString(), eq(Duration.ofMinutes(5))))
                .thenReturn(false);
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        AtomicReference<Runnable> heartbeat = new AtomicReference<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            heartbeat.set(invocation.getArgument(0));
            return future;
        }).when(executor).scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS));
        AtomicBoolean nextBatchStarted = new AtomicBoolean();

        assertThatThrownBy(() -> new JdbcSourceIngestionLock(repository, new RagProperties(), executor)
                .execute(KnowledgeSource.COMMUNITY, lease -> {
                    heartbeat.get().run();
                    lease.assertStillHeld();
                    nextBatchStarted.set(true);
                }))
                .isInstanceOf(SourceIngestionLeaseException.class)
                .hasMessageContaining("COMMUNITY 索引租约已丢失");

        assertThat(nextBatchStarted).isFalse();
        verify(repository).release(eq(KnowledgeSource.COMMUNITY), anyString());
    }
}
