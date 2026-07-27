package de.danoeh.antennapod.event.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class PlaybackHistoryEventTest {

    @Test
    fun listUpdatedReturnsNewInstanceEachCall() {
        val first = PlaybackHistoryEvent.listUpdated()
        val second = PlaybackHistoryEvent.listUpdated()
        assertNotSame(first, second)
    }

    @Test
    fun toStringIsFixedString() {
        assertEquals("PlaybackHistoryEvent", PlaybackHistoryEvent.listUpdated().toString())
    }
}
