package de.danoeh.antennapod.event.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedChangedEventTest {

    @Test
    fun newSpeedIsStored() {
        val event = SpeedChangedEvent(1.5f)
        assertEquals(1.5f, event.newSpeed, 0f)
    }
}
