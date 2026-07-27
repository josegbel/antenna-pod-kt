package de.danoeh.antennapod.event.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackPositionEventTest {

    @Test
    fun positionAndDurationAreStored() {
        val event = PlaybackPositionEvent(30, 120)
        assertEquals(30, event.position)
        assertEquals(120, event.duration)
    }
}
