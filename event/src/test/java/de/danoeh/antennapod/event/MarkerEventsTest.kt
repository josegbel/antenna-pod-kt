package de.danoeh.antennapod.event

import org.junit.Assert.assertNotNull
import org.junit.Test

class MarkerEventsTest {

    @Test
    fun streamingConfirmationEventConstructs() {
        assertNotNull(StreamingConfirmationEvent())
    }

    @Test
    fun playerStatusEventConstructs() {
        assertNotNull(PlayerStatusEvent())
    }

    @Test
    fun discoveryDefaultUpdateEventConstructs() {
        assertNotNull(DiscoveryDefaultUpdateEvent())
    }

    @Test
    fun statisticsEventConstructs() {
        assertNotNull(StatisticsEvent())
    }
}
