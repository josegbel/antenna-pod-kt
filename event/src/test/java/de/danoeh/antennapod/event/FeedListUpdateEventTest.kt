package de.danoeh.antennapod.event

import de.danoeh.antennapod.model.feed.Feed
import java.util.Arrays
import java.util.Collections
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedListUpdateEventTest {

    private fun feedWithId(id: Long): Feed {
        val feed = Feed("http://example.com/feed" + id, null)
        feed.id = id
        return feed
    }

    @Test
    fun listConstructorContainsEachFeedByIdNotIdentity() {
        val feed1 = feedWithId(1L)
        val feed2 = feedWithId(2L)
        val event = FeedListUpdateEvent(Arrays.asList(feed1, feed2))

        assertTrue(event.contains(feed1))
        assertTrue(event.contains(feed2))
        assertTrue(event.contains(feedWithId(1L)))
        assertFalse(event.contains(feedWithId(3L)))
    }

    @Test
    fun singleFeedConstructorContainsThatFeed() {
        val feed = feedWithId(5L)
        val event = FeedListUpdateEvent(feed)
        assertTrue(event.contains(feed))
        assertFalse(event.contains(feedWithId(6L)))
    }

    @Test
    fun feedIdConstructorContainsFeedWithThatId() {
        val event = FeedListUpdateEvent(9L)
        assertTrue(event.contains(feedWithId(9L)))
        assertFalse(event.contains(feedWithId(10L)))
    }

    @Test
    fun emptyListConstructorContainsNothing() {
        val event = FeedListUpdateEvent(Collections.emptyList<Feed>())
        assertFalse(event.contains(feedWithId(1L)))
    }

    @Test
    fun intLiteralResolvesToLongFeedIdConstructor() {
        val event = FeedListUpdateEvent(0)
        assertTrue(event.contains(feedWithId(0L)))
    }
}
