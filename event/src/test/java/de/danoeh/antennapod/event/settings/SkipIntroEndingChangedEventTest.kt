package de.danoeh.antennapod.event.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SkipIntroEndingChangedEventTest {

    @Test
    fun skipIntroSkipEndingAndFeedIdAreStored() {
        val event = SkipIntroEndingChangedEvent(5, 10, 7L)
        assertEquals(5, event.skipIntro)
        assertEquals(10, event.skipEnding)
        assertEquals(7L, event.feedId)
    }
}
