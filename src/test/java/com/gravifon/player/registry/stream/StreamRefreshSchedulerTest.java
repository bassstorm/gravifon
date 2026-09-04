package com.gravifon.player.registry.stream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class StreamRefreshSchedulerTest {

    @Test
    void scheduledExecutionDelegatesToService() {
        StreamRefreshService refreshService = mock(StreamRefreshService.class);
        StreamRefreshScheduler scheduler = new StreamRefreshScheduler(refreshService);

        scheduler.refreshUpcomingStreams();

        verify(refreshService).refreshUpcomingStreams();
    }
}
