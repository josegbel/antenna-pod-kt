package de.danoeh.antennapod.event;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FeedUpdateRunningEventTest {

    @Test
    public void trueIsStoredAndReadBack() {
        FeedUpdateRunningEvent event = new FeedUpdateRunningEvent(true);
        assertTrue(event.isFeedUpdateRunning);
    }

    @Test
    public void falseIsStoredAndReadBack() {
        FeedUpdateRunningEvent event = new FeedUpdateRunningEvent(false);
        assertFalse(event.isFeedUpdateRunning);
    }
}
