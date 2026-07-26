package de.danoeh.antennapod.event;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import de.danoeh.antennapod.model.feed.FeedItem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class FeedItemEventTest {

    private FeedItem itemWithId(long id) {
        FeedItem item = new FeedItem();
        item.setId(id);
        return item;
    }

    @Test
    public void itemsAndUnreadStatusChangedAreStored() {
        List<FeedItem> items = Collections.singletonList(itemWithId(1L));
        FeedItemEvent event = new FeedItemEvent(items, true);
        assertSame(items, event.items);
        assertTrue(event.unreadStatusChanged);
    }

    @Test
    public void unreadStatusChangedCanBeFalse() {
        FeedItemEvent event = new FeedItemEvent(Collections.emptyList(), false);
        assertFalse(event.unreadStatusChanged);
    }

    @Test
    public void indexOfItemWithIdReturnsMatchingIndex() {
        List<FeedItem> items = Arrays.asList(itemWithId(1L), itemWithId(2L), itemWithId(3L));
        assertEquals(1, FeedItemEvent.indexOfItemWithId(items, 2L));
    }

    @Test
    public void indexOfItemWithIdReturnsMinusOneWhenMissing() {
        List<FeedItem> items = Arrays.asList(itemWithId(1L), itemWithId(2L));
        assertEquals(-1, FeedItemEvent.indexOfItemWithId(items, 42L));
    }

    @Test
    public void indexOfItemWithIdReturnsMinusOneForEmptyList() {
        assertEquals(-1, FeedItemEvent.indexOfItemWithId(Collections.emptyList(), 1L));
    }

    @Test
    public void indexOfItemWithIdToleratesNullElements() {
        List<FeedItem> items = new ArrayList<>();
        items.add(null);
        items.add(itemWithId(5L));
        assertEquals(1, FeedItemEvent.indexOfItemWithId(items, 5L));
    }

    @Test
    public void constructorRejectsNullItemsAfterConversion() {
        assertThrows(NullPointerException.class, () -> new FeedItemEvent(null, false));
    }
}
