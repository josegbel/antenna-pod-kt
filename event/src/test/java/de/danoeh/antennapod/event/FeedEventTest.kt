package de.danoeh.antennapod.event

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedEventTest {

    @Test
    fun feedIdIsStoredAndReadAsField() {
        val event = FeedEvent(FeedEvent.Action.FILTER_CHANGED, 7L)
        assertEquals(7L, event.feedId)
    }

    @Test
    fun toStringIncludesActionAndFeedId() {
        val event = FeedEvent(FeedEvent.Action.SORT_ORDER_CHANGED, 99L)
        assertEquals("FeedEvent{action=SORT_ORDER_CHANGED, feedId=99}", event.toString())
    }
}
