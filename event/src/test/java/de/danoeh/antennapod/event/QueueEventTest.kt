package de.danoeh.antennapod.event

import de.danoeh.antennapod.model.feed.FeedItem
import java.util.Arrays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class QueueEventTest {

    private fun itemWithId(id: Long): FeedItem {
        val item = FeedItem()
        item.id = id
        return item
    }

    @Test
    fun addedSetsActionItemAndPosition() {
        val item = itemWithId(1L)
        val event = QueueEvent.added(item, 5)
        assertSame(QueueEvent.Action.ADDED, event.action)
        assertSame(item, event.item)
        assertNull(event.items)
        assertEquals(5, event.position)
    }

    @Test
    fun setQueueSetsActionAndItemsWithMinusOnePosition() {
        val queue = Arrays.asList(itemWithId(1L), itemWithId(2L))
        val event = QueueEvent.setQueue(queue)
        assertSame(QueueEvent.Action.SET_QUEUE, event.action)
        assertNull(event.item)
        assertSame(queue, event.items)
        assertEquals(-1, event.position)
    }

    @Test
    fun removedSetsActionAndItemWithMinusOnePosition() {
        val item = itemWithId(3L)
        val event = QueueEvent.removed(item)
        assertSame(QueueEvent.Action.REMOVED, event.action)
        assertSame(item, event.item)
        assertNull(event.items)
        assertEquals(-1, event.position)
    }

    @Test
    fun irreversibleRemovedSetsActionAndItemWithMinusOnePosition() {
        val item = itemWithId(4L)
        val event = QueueEvent.irreversibleRemoved(item)
        assertSame(QueueEvent.Action.IRREVERSIBLE_REMOVED, event.action)
        assertSame(item, event.item)
        assertNull(event.items)
        assertEquals(-1, event.position)
    }

    @Test
    fun clearedSetsActionOnlyWithNullItemAndItemsAndMinusOnePosition() {
        val event = QueueEvent.cleared()
        assertSame(QueueEvent.Action.CLEARED, event.action)
        assertNull(event.item)
        assertNull(event.items)
        assertEquals(-1, event.position)
    }

    @Test
    fun sortedSetsActionAndItemsWithMinusOnePosition() {
        val sortedQueue = Arrays.asList(itemWithId(2L), itemWithId(1L))
        val event = QueueEvent.sorted(sortedQueue)
        assertSame(QueueEvent.Action.SORTED, event.action)
        assertNull(event.item)
        assertSame(sortedQueue, event.items)
        assertEquals(-1, event.position)
    }

    @Test
    fun movedSetsActionItemAndNewPosition() {
        val item = itemWithId(6L)
        val event = QueueEvent.moved(item, 8)
        assertSame(QueueEvent.Action.MOVED, event.action)
        assertSame(item, event.item)
        assertNull(event.items)
        assertEquals(8, event.position)
    }

    @Test
    fun actionEnumHasNineConstantsInDeclaredOrder() {
        val values = QueueEvent.Action.values()
        assertEquals(9, values.size)
        assertEquals(0, QueueEvent.Action.ADDED.ordinal)
        assertEquals(1, QueueEvent.Action.ADDED_ITEMS.ordinal)
        assertEquals(2, QueueEvent.Action.SET_QUEUE.ordinal)
        assertEquals(3, QueueEvent.Action.REMOVED.ordinal)
        assertEquals(4, QueueEvent.Action.IRREVERSIBLE_REMOVED.ordinal)
        assertEquals(5, QueueEvent.Action.CLEARED.ordinal)
        assertEquals(6, QueueEvent.Action.DELETED_MEDIA.ordinal)
        assertEquals(7, QueueEvent.Action.SORTED.ordinal)
        assertEquals(8, QueueEvent.Action.MOVED.ordinal)
    }
}
