package de.danoeh.antennapod.model.feed;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FeedOrderTest {

    @Test
    public void testIdConstants() {
        assertEquals(0, FeedOrder.COUNTER.id);
        assertEquals(1, FeedOrder.ALPHABETICAL.id);
        assertEquals(3, FeedOrder.MOST_PLAYED.id);
        assertEquals(2, FeedOrder.MOST_RECENT_EPISODE.id);
    }

    @Test
    public void testFromOrdinalKnownIds() {
        assertEquals(FeedOrder.COUNTER, FeedOrder.fromOrdinal(0));
        assertEquals(FeedOrder.ALPHABETICAL, FeedOrder.fromOrdinal(1));
        assertEquals(FeedOrder.MOST_RECENT_EPISODE, FeedOrder.fromOrdinal(2));
        assertEquals(FeedOrder.MOST_PLAYED, FeedOrder.fromOrdinal(3));
    }

    @Test
    public void testFromOrdinalUnknownIdReturnsDefault() {
        assertEquals(FeedOrder.MOST_RECENT_EPISODE, FeedOrder.fromOrdinal(99));
        assertEquals(FeedOrder.MOST_RECENT_EPISODE, FeedOrder.fromOrdinal(-1));
    }
}
