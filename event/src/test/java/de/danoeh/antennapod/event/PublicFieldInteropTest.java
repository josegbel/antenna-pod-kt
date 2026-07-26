package de.danoeh.antennapod.event;

import android.content.Context;

import androidx.core.util.Consumer;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

import de.danoeh.antennapod.event.playback.PlaybackServiceEvent;
import de.danoeh.antennapod.model.feed.FeedItem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PublicFieldInteropTest {

    @Test
    public void feedUpdateRunningEventIsFeedUpdateRunningReadAsField() {
        FeedUpdateRunningEvent event = new FeedUpdateRunningEvent(true);
        assertTrue(event.isFeedUpdateRunning);
    }

    @Test
    public void playbackServiceEventActionReadAsField() {
        PlaybackServiceEvent event = new PlaybackServiceEvent(PlaybackServiceEvent.Action.SERVICE_STARTED);
        assertSame(PlaybackServiceEvent.Action.SERVICE_STARTED, event.action);
    }

    @Test
    public void feedEventFeedIdReadAsField() {
        FeedEvent event = new FeedEvent(FeedEvent.Action.FILTER_CHANGED, 42L);
        assertEquals(42L, event.feedId);
    }

    @Test
    public void feedItemEventItemsAndUnreadStatusChangedReadAsFields() {
        List<FeedItem> items = Collections.singletonList(new FeedItem());
        FeedItemEvent event = new FeedItemEvent(items, true);
        assertSame(items, event.items);
        assertTrue(event.unreadStatusChanged);
    }

    @Test
    public void queueEventActionItemItemsPositionReadAsFields() {
        FeedItem item = new FeedItem();
        QueueEvent event = QueueEvent.added(item, 3);
        assertSame(QueueEvent.Action.ADDED, event.action);
        assertSame(item, event.item);
        assertNull(event.items);
        assertEquals(3, event.position);
    }

    @Test
    public void messageEventMessageActionActionTextReadAsFields() {
        Consumer<Context> action = ignored -> { };
        MessageEvent event = new MessageEvent("hello", action, "Undo");
        assertEquals("hello", event.message);
        assertSame(action, event.action);
        assertEquals("Undo", event.actionText);
    }

    @Test
    public void messageEventOneArgConstructorLeavesActionAndActionTextNull() {
        MessageEvent event = new MessageEvent("hello");
        assertEquals("hello", event.message);
        assertNull(event.action);
        assertNull(event.actionText);
    }

    @Test
    public void queueEventClearedHasNullItemAndItemsAndMinusOnePosition() {
        QueueEvent event = QueueEvent.cleared();
        assertSame(QueueEvent.Action.CLEARED, event.action);
        assertNull(event.item);
        assertNull(event.items);
        assertEquals(-1, event.position);
    }
}
