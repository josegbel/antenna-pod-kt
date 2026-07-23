package de.danoeh.antennapod.model.feed;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;

import static de.danoeh.antennapod.model.feed.FeedItemMother.anyFeedItemWithImage;
import static de.danoeh.antennapod.model.feed.FeedMother.anyFeed;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

public class FeedItemTest {

    private static final String TEXT_LONG = "Lorem ipsum dolor sit amet, consectetur adipiscing elit.";
    private static final String TEXT_SHORT = "Lorem ipsum";

    private FeedItem original;
    private FeedItem changedFeedItem;

    @Before
    public void setUp() {
        original = anyFeedItemWithImage();
        changedFeedItem = anyFeedItemWithImage();
    }

    @Test
    public void testUpdateFromOther_feedItemImageDownloadUrlChanged() {
        setNewFeedItemImageDownloadUrl();
        original.updateFromOther(changedFeedItem);
        assertFeedItemImageWasUpdated();
    }

    @Test
    public void testUpdateFromOther_feedItemImageRemoved() {
        feedItemImageRemoved();
        original.updateFromOther(changedFeedItem);
        assertFeedItemImageWasNotUpdated();
    }

    @Test
    public void testUpdateFromOther_feedItemImageAdded() {
        original.setImageUrl(null);
        setNewFeedItemImageDownloadUrl();
        original.updateFromOther(changedFeedItem);
        assertFeedItemImageWasUpdated();
    }

    @Test
    public void testUpdateFromOther_dateChanged() throws Exception {
        Date originalDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("1952-03-11 00:00:00");
        Date changedDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("1952-03-11 00:42:42");
        original.setPubDate(originalDate);
        changedFeedItem.setPubDate(changedDate);
        original.updateFromOther(changedFeedItem);
        assertEquals(changedDate.getTime(), original.getPubDate().getTime());
    }

    /**
     * Test that a played item loses that state after being marked as new.
     */
    @Test
    public void testMarkPlayedItemAsNew_itemNotPlayed() {
        original.setPlayed(true);
        original.setNew();

        assertFalse(original.isPlayed());
    }

    /**
     * Test that a new item loses that state after being marked as played.
     */
    @Test
    public void testMarkNewItemAsPlayed_itemNotNew() {
        original.setNew();
        original.setPlayed(true);

        assertFalse(original.isNew());
    }

    /**
     * Test that a new item loses that state after being marked as not played.
     */
    @Test
    public void testMarkNewItemAsNotPlayed_itemNotNew() {
        original.setNew();
        original.setPlayed(false);

        assertFalse(original.isNew());
    }

    private void setNewFeedItemImageDownloadUrl() {
        changedFeedItem.setImageUrl("http://example.com/new_picture");
    }

    private void feedItemImageRemoved() {
        changedFeedItem.setImageUrl(null);
    }

    private void assertFeedItemImageWasUpdated() {
        assertEquals(original.getImageUrl(), changedFeedItem.getImageUrl());
    }

    private void assertFeedItemImageWasNotUpdated() {
        assertEquals(anyFeedItemWithImage().getImageUrl(), original.getImageUrl());
    }

    /**
     * If one of `description` or `content:encoded` is null, use the other one.
     */
    @Test
    public void testShownotesNullValues() {
        testShownotes(null, TEXT_LONG);
        testShownotes(TEXT_LONG, null);
    }

    /**
     * If `description` is reasonably longer than `content:encoded`, use `description`.
     */
    @Test
    public void testShownotesLength() {
        testShownotes(TEXT_SHORT, TEXT_LONG);
        testShownotes(TEXT_LONG, TEXT_SHORT);
    }

    /**
     * Checks if the shownotes equal TEXT_LONG, using the given `description` and `content:encoded`.
     *
     * @param description Description of the feed item
     * @param contentEncoded `content:encoded` of the feed item
     */
    private void testShownotes(String description, String contentEncoded) {
        FeedItem item = new FeedItem();
        item.setDescriptionIfLonger(description);
        item.setDescriptionIfLonger(contentEncoded);
        assertEquals(TEXT_LONG, item.getDescription());
    }

    @Test
    public void equalsSameIdIsEqual() {
        FeedItem a = anyFeedItemWithImage();
        FeedItem b = anyFeedItemWithImage();
        a.setId(1);
        b.setId(1);
        assertEquals(a, b);
    }

    @Test
    public void equalsDifferentIdNotEqual() {
        FeedItem a = anyFeedItemWithImage();
        FeedItem b = anyFeedItemWithImage();
        a.setId(1);
        b.setId(2);
        assertNotEquals(a, b);
    }

    @Test
    public void hashCodeMatchesForSameId() {
        FeedItem a = anyFeedItemWithImage();
        FeedItem b = anyFeedItemWithImage();
        a.setId(1);
        b.setId(1);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void getIdentifyingValuePrefersItemIdentifier() {
        FeedItem item = createItem("item-identifier", "title", "http://example.com/link");
        assertEquals("item-identifier", item.getIdentifyingValue());
    }

    @Test
    public void getIdentifyingValueFallsBackToTitle() {
        FeedItem item = createItem(null, "title", "http://example.com/link");
        assertEquals("title", item.getIdentifyingValue());
    }

    @Test
    public void getIdentifyingValueFallsBackToLink() {
        FeedItem item = createItem(null, null, "http://example.com/link");
        assertEquals("http://example.com/link", item.getIdentifyingValue());
    }

    @Test
    public void getPubDateReturnsDefensiveCopy() {
        FeedItem item = new FeedItem();
        assertNull(item.getPubDate());

        Date original = new Date(123456789L);
        item.setPubDate(original);
        Date returned = item.getPubDate();
        returned.setTime(0L);

        assertEquals(123456789L, item.getPubDate().getTime());
    }

    @Test
    public void setPubDateStoresDefensiveCopy() {
        FeedItem item = new FeedItem();
        Date source = new Date(123456789L);
        item.setPubDate(source);
        source.setTime(0L);

        assertEquals(123456789L, item.getPubDate().getTime());
    }

    @Test
    public void serializationDropsTransientFeedAndChapters() throws Exception {
        // media/transcript are intentionally left null: both are non-transient and media is typed
        // FeedMedia (Tier C, implements Playable -> Parcelable), so populating it could drag an
        // accidental Tier C dependency (and a possible NotSerializableException) into this bare-JVM
        // Tier A test.
        FeedItem item = new FeedItem();
        item.setId(42);
        item.setTitle("Serialize me");
        item.setFeed(anyFeed());
        item.setChapters(Collections.singletonList(new Chapter(0, "Chapter 1", null, null)));
        Date pubDate = new Date(987654321L);
        item.setPubDate(pubDate);

        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(byteStream)) {
            out.writeObject(item);
        }
        FeedItem deserialized;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(byteStream.toByteArray()))) {
            deserialized = (FeedItem) in.readObject();
        }

        assertEquals(42, deserialized.getId());
        assertEquals("Serialize me", deserialized.getTitle());
        assertNull(deserialized.getFeed());
        assertNull(deserialized.getChapters());
        // pubDate's backing field was renamed pubDate -> pubDateField during the Kotlin conversion
        // (see the disclosure comment above FeedItem.kt's pubDateField); this assertion pins that the
        // non-transient field still survives an intra-session ObjectOutputStream/ObjectInputStream
        // round trip under its new name.
        assertEquals(pubDate, deserialized.getPubDate());
    }

    private FeedItem createItem(String itemIdentifier, String title, String link) {
        return new FeedItem(1, title, itemIdentifier, link, null, FeedItem.UNPLAYED, null);
    }
}
