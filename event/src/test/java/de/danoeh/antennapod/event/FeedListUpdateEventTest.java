package de.danoeh.antennapod.event;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import de.danoeh.antennapod.model.feed.Feed;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FeedListUpdateEventTest {

    private Feed feedWithId(long id) {
        Feed feed = new Feed("http://example.com/feed" + id, null);
        feed.setId(id);
        return feed;
    }

    @Test
    public void listConstructorContainsEachFeedByIdNotIdentity() {
        Feed feed1 = feedWithId(1L);
        Feed feed2 = feedWithId(2L);
        FeedListUpdateEvent event = new FeedListUpdateEvent(Arrays.asList(feed1, feed2));

        assertTrue(event.contains(feed1));
        assertTrue(event.contains(feed2));
        assertTrue(event.contains(feedWithId(1L)));
        assertFalse(event.contains(feedWithId(3L)));
    }

    @Test
    public void singleFeedConstructorContainsThatFeed() {
        Feed feed = feedWithId(5L);
        FeedListUpdateEvent event = new FeedListUpdateEvent(feed);
        assertTrue(event.contains(feed));
        assertFalse(event.contains(feedWithId(6L)));
    }

    @Test
    public void feedIdConstructorContainsFeedWithThatId() {
        FeedListUpdateEvent event = new FeedListUpdateEvent(9L);
        assertTrue(event.contains(feedWithId(9L)));
        assertFalse(event.contains(feedWithId(10L)));
    }

    @Test
    public void emptyListConstructorContainsNothing() {
        FeedListUpdateEvent event = new FeedListUpdateEvent(Collections.<Feed>emptyList());
        assertFalse(event.contains(feedWithId(1L)));
    }

    @Test
    public void intLiteralResolvesToLongFeedIdConstructor() {
        FeedListUpdateEvent event = new FeedListUpdateEvent(0);
        assertTrue(event.contains(feedWithId(0L)));
    }
}
