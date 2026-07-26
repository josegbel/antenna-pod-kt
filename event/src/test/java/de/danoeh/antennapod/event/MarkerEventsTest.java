package de.danoeh.antennapod.event;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class MarkerEventsTest {

    @Test
    public void streamingConfirmationEventConstructs() {
        assertNotNull(new StreamingConfirmationEvent());
    }

    @Test
    public void playerStatusEventConstructs() {
        assertNotNull(new PlayerStatusEvent());
    }

    @Test
    public void discoveryDefaultUpdateEventConstructs() {
        assertNotNull(new DiscoveryDefaultUpdateEvent());
    }

    @Test
    public void statisticsEventConstructs() {
        assertNotNull(new StatisticsEvent());
    }
}
