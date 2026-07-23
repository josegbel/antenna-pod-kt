package de.danoeh.antennapod.model.feed;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

import static de.danoeh.antennapod.model.feed.FeedMother.IMAGE_URL;
import static de.danoeh.antennapod.model.feed.FeedMother.anyFeed;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

public class FeedTest {

    private Feed original;
    private Feed changedFeed;

    @Before
    public void setUp() {
        original = anyFeed();
        changedFeed = anyFeed();
    }

    @Test
    public void testUpdateFromOther_feedImageDownloadUrlChanged() {
        changedFeed.setImageUrl("http://example.com/new_picture");
        original.updateFromOther(changedFeed);
        assertEquals(original.getImageUrl(), changedFeed.getImageUrl());
    }

    @Test
    public void testUpdateFromOther_feedImageRemoved() {
        changedFeed.setImageUrl(null);
        original.updateFromOther(changedFeed);
        assertEquals(anyFeed().getImageUrl(), original.getImageUrl());
    }

    @Test
    public void testUpdateFromOther_feedImageAdded() {
        original.setImageUrl(null);
        changedFeed.setImageUrl("http://example.com/new_picture");
        original.updateFromOther(changedFeed);
        assertEquals(original.getImageUrl(), changedFeed.getImageUrl());
    }

    @Test
    public void testSetSortOrder_OnlyIntraFeedSortAllowed() {
        for (SortOrder sortOrder : SortOrder.values()) {
            if (sortOrder.scope == SortOrder.Scope.INTRA_FEED) {
                original.setSortOrder(sortOrder); // should be okay
            } else {
                assertThrows(IllegalArgumentException.class, () -> original.setSortOrder(sortOrder));
            }
        }
    }

    @Test
    public void testSetSortOrder_NullAllowed() {
        original.setSortOrder(null); // should be okay
    }

    @Test
    public void equalsSameIdIsEqual() {
        Feed a = anyFeed();
        Feed b = anyFeed();
        a.setId(1);
        b.setId(1);
        assertEquals(a, b);
    }

    @Test
    public void equalsDifferentIdNotEqual() {
        Feed a = anyFeed();
        Feed b = anyFeed();
        a.setId(1);
        b.setId(2);
        assertNotEquals(a, b);
    }

    @Test
    public void equalsDifferentClassNotEqual() {
        Feed a = anyFeed();
        assertNotEquals(a, "not a feed");
    }

    @Test
    public void hashCodeMatchesForSameId() {
        Feed a = anyFeed();
        Feed b = anyFeed();
        a.setId(1);
        b.setId(1);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void getIdentifyingValuePrefersFeedIdentifier() {
        Feed feed = createFeed("feed-identifier", "http://example.com/download", "title", null, "http://example.com/link");
        assertEquals("feed-identifier", feed.getIdentifyingValue());
    }

    @Test
    public void getIdentifyingValueFallsBackToDownloadUrl() {
        Feed feed = createFeed(null, "http://example.com/download", "title", null, "http://example.com/link");
        assertEquals("http://example.com/download", feed.getIdentifyingValue());
    }

    @Test
    public void getIdentifyingValueFallsBackToTitle() {
        Feed feed = createFeed(null, null, "title", null, "http://example.com/link");
        assertEquals("title", feed.getIdentifyingValue());
    }

    @Test
    public void getIdentifyingValueFallsBackToLink() {
        Feed feed = createFeed(null, null, null, null, "http://example.com/link");
        assertEquals("http://example.com/link", feed.getIdentifyingValue());
    }

    @Test
    public void getHumanReadableIdentifierPrefersCustomTitle() {
        Feed feed = createFeed(null, "http://example.com/download", "title", "custom title", "http://example.com/link");
        assertEquals("custom title", feed.getHumanReadableIdentifier());
    }

    @Test
    public void getHumanReadableIdentifierFallsBackToFeedTitle() {
        Feed feed = createFeed(null, "http://example.com/download", "title", null, "http://example.com/link");
        assertEquals("title", feed.getHumanReadableIdentifier());
    }

    @Test
    public void getHumanReadableIdentifierFallsBackToDownloadUrl() {
        Feed feed = createFeed(null, "http://example.com/download", null, null, "http://example.com/link");
        assertEquals("http://example.com/download", feed.getHumanReadableIdentifier());
    }

    @Test
    public void constructorParsesFundingLinks() {
        Feed feed = new Feed(1, null, "title", "http://example.com/link", "description",
                "http://example.com/pay", "author", "en", null, null, IMAGE_URL, null,
                "http://example.com/feed", 0);
        ArrayList<FeedFunding> paymentLinks = feed.getPaymentLinks();
        assertEquals(1, paymentLinks.size());
        assertEquals("http://example.com/pay", paymentLinks.get(0).url);
    }

    @Test
    public void serializationRoundTripPreservesIdTitleAndFunding() throws Exception {
        Feed feed = anyFeed();
        feed.setId(99);
        feed.setCustomTitle("My Custom Title");

        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(byteStream)) {
            out.writeObject(feed);
        }
        Feed deserialized;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(byteStream.toByteArray()))) {
            deserialized = (Feed) in.readObject();
        }

        assertEquals(99, deserialized.getId());
        assertEquals(feed.getFeedTitle(), deserialized.getFeedTitle());
        assertEquals("My Custom Title", deserialized.getCustomTitle());
        assertEquals(feed.getPaymentLinks(), deserialized.getPaymentLinks());
    }

    private Feed createFeed(String feedIdentifier, String downloadUrl, String title, String customTitle,
                             String link) {
        return new Feed(1, null, title, customTitle, link, null, null, null, null, null,
                feedIdentifier, null, null, downloadUrl, 0, false, null, null, null, false, Feed.STATE_SUBSCRIBED);
    }
}