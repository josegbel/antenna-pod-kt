package de.danoeh.antennapod.event;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import de.danoeh.antennapod.model.feed.FeedItem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class QueueEventTest {

    private FeedItem itemWithId(long id) {
        FeedItem item = new FeedItem();
        item.setId(id);
        return item;
    }

    @Test
    public void addedSetsActionItemAndPosition() {
        FeedItem item = itemWithId(1L);
        QueueEvent event = QueueEvent.added(item, 5);
        assertSame(QueueEvent.Action.ADDED, event.action);
        assertSame(item, event.item);
        assertNull(event.items);
        assertEquals(5, event.position);
    }

    @Test
    public void setQueueSetsActionAndItemsWithMinusOnePosition() {
        List<FeedItem> queue = Arrays.asList(itemWithId(1L), itemWithId(2L));
        QueueEvent event = QueueEvent.setQueue(queue);
        assertSame(QueueEvent.Action.SET_QUEUE, event.action);
        assertNull(event.item);
        assertSame(queue, event.items);
        assertEquals(-1, event.position);
    }

    @Test
    public void removedSetsActionAndItemWithMinusOnePosition() {
        FeedItem item = itemWithId(3L);
        QueueEvent event = QueueEvent.removed(item);
        assertSame(QueueEvent.Action.REMOVED, event.action);
        assertSame(item, event.item);
        assertNull(event.items);
        assertEquals(-1, event.position);
    }

    @Test
    public void irreversibleRemovedSetsActionAndItemWithMinusOnePosition() {
        FeedItem item = itemWithId(4L);
        QueueEvent event = QueueEvent.irreversibleRemoved(item);
        assertSame(QueueEvent.Action.IRREVERSIBLE_REMOVED, event.action);
        assertSame(item, event.item);
        assertNull(event.items);
        assertEquals(-1, event.position);
    }

    @Test
    public void clearedSetsActionOnlyWithNullItemAndItemsAndMinusOnePosition() {
        QueueEvent event = QueueEvent.cleared();
        assertSame(QueueEvent.Action.CLEARED, event.action);
        assertNull(event.item);
        assertNull(event.items);
        assertEquals(-1, event.position);
    }

    @Test
    public void sortedSetsActionAndItemsWithMinusOnePosition() {
        List<FeedItem> sortedQueue = Arrays.asList(itemWithId(2L), itemWithId(1L));
        QueueEvent event = QueueEvent.sorted(sortedQueue);
        assertSame(QueueEvent.Action.SORTED, event.action);
        assertNull(event.item);
        assertSame(sortedQueue, event.items);
        assertEquals(-1, event.position);
    }

    @Test
    public void movedSetsActionItemAndNewPosition() {
        FeedItem item = itemWithId(6L);
        QueueEvent event = QueueEvent.moved(item, 8);
        assertSame(QueueEvent.Action.MOVED, event.action);
        assertSame(item, event.item);
        assertNull(event.items);
        assertEquals(8, event.position);
    }

    @Test
    public void actionEnumHasNineConstantsInDeclaredOrder() {
        QueueEvent.Action[] values = QueueEvent.Action.values();
        assertEquals(9, values.length);
        assertEquals(0, QueueEvent.Action.ADDED.ordinal());
        assertEquals(1, QueueEvent.Action.ADDED_ITEMS.ordinal());
        assertEquals(2, QueueEvent.Action.SET_QUEUE.ordinal());
        assertEquals(3, QueueEvent.Action.REMOVED.ordinal());
        assertEquals(4, QueueEvent.Action.IRREVERSIBLE_REMOVED.ordinal());
        assertEquals(5, QueueEvent.Action.CLEARED.ordinal());
        assertEquals(6, QueueEvent.Action.DELETED_MEDIA.ordinal());
        assertEquals(7, QueueEvent.Action.SORTED.ordinal());
        assertEquals(8, QueueEvent.Action.MOVED.ordinal());
    }
}
