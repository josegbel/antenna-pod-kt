package de.danoeh.antennapod.event;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FeedEventTest {

    @Test
    public void feedIdIsStoredAndReadAsField() {
        FeedEvent event = new FeedEvent(FeedEvent.Action.FILTER_CHANGED, 7L);
        assertEquals(7L, event.feedId);
    }

    @Test
    public void toStringIncludesActionAndFeedId() {
        FeedEvent event = new FeedEvent(FeedEvent.Action.SORT_ORDER_CHANGED, 99L);
        assertEquals("FeedEvent{action=SORT_ORDER_CHANGED, feedId=99}", event.toString());
    }
}
