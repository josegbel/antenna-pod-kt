package de.danoeh.antennapod.event.settings

import de.danoeh.antennapod.model.feed.FeedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SpeedPresetChangedEventTest {

    @Test
    fun speedFeedIdAndSkipSilenceAreStored() {
        val event =
            SpeedPresetChangedEvent(1.25f, 11L, FeedPreferences.SkipSilence.AGGRESSIVE)
        assertEquals(1.25f, event.speed, 0f)
        assertEquals(11L, event.feedId)
        assertSame(FeedPreferences.SkipSilence.AGGRESSIVE, event.skipSilence)
    }
}
