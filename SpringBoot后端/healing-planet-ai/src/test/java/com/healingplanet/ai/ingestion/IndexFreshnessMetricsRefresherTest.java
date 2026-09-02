package com.healingplanet.ai.ingestion;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class IndexFreshnessMetricsRefresherTest {

    @Test
    void shouldRefreshMetricsThroughTheReadOnlyStatusPath() {
        IndexStatusService statusService = mock(IndexStatusService.class);

        new IndexFreshnessMetricsRefresher(statusService).refresh();

        verify(statusService).status();
    }

    @Test
    void shouldContainProbeFailureWithoutStartingAnyIndexRun() {
        IndexStatusService statusService = mock(IndexStatusService.class);
        doThrow(new IllegalStateException("database unavailable")).when(statusService).status();

        new IndexFreshnessMetricsRefresher(statusService).refresh();

        verify(statusService).status();
    }
}
