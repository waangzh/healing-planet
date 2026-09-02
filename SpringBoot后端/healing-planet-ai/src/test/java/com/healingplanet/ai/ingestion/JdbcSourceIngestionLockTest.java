package com.healingplanet.ai.ingestion;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.domain.KnowledgeSource;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
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
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future).when(executor)
                .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS));
        AtomicBoolean executed = new AtomicBoolean();

        new JdbcSourceIngestionLock(repository, new RagProperties(), executor)
                .execute(KnowledgeSource.COMMUNITY, () -> executed.set(true));

        assertThat(executed).isTrue();
        verify(executor).scheduleAtFixedRate(any(Runnable.class), eq(100_000L), eq(100_000L),
                eq(TimeUnit.MILLISECONDS));
        verify(repository).release(eq(KnowledgeSource.COMMUNITY), anyString());
        verify(future).cancel(false);
    }
}
