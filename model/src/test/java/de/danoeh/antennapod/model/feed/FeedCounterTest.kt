package de.danoeh.antennapod.model.feed

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedCounterTest {

    @Test
    fun testIdConstants() {
        assertEquals(1, FeedCounter.SHOW_NEW.id)
        assertEquals(2, FeedCounter.SHOW_UNPLAYED.id)
        assertEquals(3, FeedCounter.SHOW_NONE.id)
        assertEquals(4, FeedCounter.SHOW_DOWNLOADED.id)
        assertEquals(5, FeedCounter.SHOW_DOWNLOADED_UNPLAYED.id)
    }

    @Test
    fun testFromOrdinalKnownIds() {
        assertEquals(FeedCounter.SHOW_NEW, FeedCounter.fromOrdinal(1))
        assertEquals(FeedCounter.SHOW_UNPLAYED, FeedCounter.fromOrdinal(2))
        assertEquals(FeedCounter.SHOW_NONE, FeedCounter.fromOrdinal(3))
        assertEquals(FeedCounter.SHOW_DOWNLOADED, FeedCounter.fromOrdinal(4))
        assertEquals(FeedCounter.SHOW_DOWNLOADED_UNPLAYED, FeedCounter.fromOrdinal(5))
    }

    @Test
    fun testFromOrdinalUnknownIdReturnsDefault() {
        assertEquals(FeedCounter.SHOW_NONE, FeedCounter.fromOrdinal(0))
        assertEquals(FeedCounter.SHOW_NONE, FeedCounter.fromOrdinal(99))
    }
}
