package de.danoeh.antennapod.model.feed;

import android.os.Parcel;
import android.support.v4.media.MediaBrowserCompat;

import de.danoeh.antennapod.model.playback.RemoteMedia;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;
import java.util.Date;

import static de.danoeh.antennapod.model.feed.FeedMediaMother.anyFeedMedia;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Runs under Robolectric (this milestone's disclosed one-milestone exception to :model's
// Robolectric-free precedent) - needed for real android.os.Parcel round trips and for
// FeedMedia.getMediaItem()'s android.net.Uri.parse/MediaBrowserCompat bridge.
@RunWith(RobolectricTestRunner.class)
public class FeedMediaTest {

    private FeedMedia media;

    @Before
    public void setUp() {
        media = anyFeedMedia();
    }

    /**
     * Downloading a media from a not new and not played item should not change the item state.
     */
    @Test
    public void testDownloadMediaOfNotNewAndNotPlayedItem_unchangedItemState() {
        FeedItem item = mock(FeedItem.class);
        when(item.isNew()).thenReturn(false);
        when(item.isPlayed()).thenReturn(false);

        media.setItem(item);
        media.setDownloaded(true, System.currentTimeMillis());

        verify(item, never()).setNew();
        verify(item, never()).setPlayed(true);
        verify(item, never()).setPlayed(false);
    }

    /**
     * Downloading a media from a played item (thus not new) should not change the item state.
     */
    @Test
    public void testDownloadMediaOfPlayedItem_unchangedItemState() {
        FeedItem item = mock(FeedItem.class);
        when(item.isNew()).thenReturn(false);
        when(item.isPlayed()).thenReturn(true);

        media.setItem(item);
        media.setDownloaded(true, System.currentTimeMillis());

        verify(item, never()).setNew();
        verify(item, never()).setPlayed(true);
        verify(item, never()).setPlayed(false);
    }

    /**
     * Downloading a media from a new item (thus not played) should change the item to not played.
     */
    @Test
    public void testDownloadMediaOfNewItem_changedToNotPlayedItem() {
        FeedItem item = mock(FeedItem.class);
        when(item.isNew()).thenReturn(true);
        when(item.isPlayed()).thenReturn(false);

        media.setItem(item);
        media.setDownloaded(true, System.currentTimeMillis());

        verify(item).setPlayed(false);
        verify(item, never()).setNew();
        verify(item, never()).setPlayed(true);
    }

    @Test
    public void parcelRoundTripDropsItemKeepsItemIdAndConvertsNullDateToEpoch() throws Exception {
        Feed feed = new Feed("http://example.com/feed", null, "feedTitle");
        FeedItem item = new FeedItem(777, "title", "item-id", "http://example.com/item",
                new Date(), FeedItem.UNPLAYED, feed);

        FeedMedia original = new FeedMedia(42, item, 1000, 500, 2000, "audio/mp3", "/local/file.mp3",
                "http://example.com/ep.mp3", 123456789L, null, 1000, 999L);
        original.setPlayedDuration(5000);
        // Pre-condition: playedDurationWhenStarted (set at construction to the original
        // playedDuration, 1000) genuinely diverges from the now-advanced playedDuration (5000),
        // so the round trip's asymmetry below is actually discriminated, not coincidentally
        // matching.
        assertNotEquals(original.getPlayedDuration(), original.getPlayedDurationWhenStarted());

        FeedMedia restored = parcelRoundTrip(original);

        assertNull(restored.getItem());
        assertEquals(item.getId(), restored.getItemId());
        assertEquals(new Date(0), restored.getLastPlayedTimeHistory());
        // Not parceled - field-initializer default, NOT 0.
        assertEquals(-1, restored.getStartPosition());
        // Not parceled - but the 12-arg constructor the CREATOR calls unconditionally sets
        // playedDurationWhenStarted = playedDuration, so it tracks the restored (parceled)
        // playedDuration, not the original's pre-parcel snapshot (1000).
        assertEquals(5000, restored.getPlayedDuration());
        assertEquals(restored.getPlayedDuration(), restored.getPlayedDurationWhenStarted());
        // Not parceled - the 12-arg constructor never sets it (only the 13-arg overload does),
        // so it restores to its unset null ("unknown, will be checked"). No public getter reads
        // the raw field without triggering computation, so reflection pins the exact state.
        assertNull(getHasEmbeddedPictureField(restored));
    }

    @Test
    public void equalsIsIdOnly() {
        FeedMedia a = anyFeedMedia();
        a.setId(5);
        FeedMedia b = new FeedMedia(null, "http://example.com/other.mp3", 999, "audio/ogg");
        b.setId(5);
        assertEquals(a, b);

        FeedMedia c = anyFeedMedia();
        c.setId(6);
        assertNotEquals(a, c);
    }

    @Test
    public void feedMediaEqualsRemoteMediaDelegatesSymmetrically() {
        Feed feed = new Feed("http://example.com/feed", null, "feedTitle");
        FeedItem item = new FeedItem(1, "title", "item-id-1", "http://example.com/item",
                new Date(), FeedItem.UNPLAYED, feed);
        FeedMedia fm = new FeedMedia(item, "http://example.com/ep.mp3", 100, "audio/mp3");
        fm.setId(42);

        RemoteMedia rm = new RemoteMedia("http://example.com/ep.mp3", "item-id-1", "http://example.com/feed",
                "feedTitle", "title", "http://example.com/item", "author", "image", "feedlink",
                "audio/mp3", new Date(), "notes");

        assertTrue(fm.equals(rm));
        assertTrue(rm.equals(fm));
    }

    @Test
    public void getMediaItemMapsFieldsAndIconUri() {
        Feed feed = new Feed("http://example.com/feed", null, "feedTitle");
        feed.setImageUrl("http://example.com/feed.png");
        FeedItem item = new FeedItem(10, "Episode Title", "item-id", "http://example.com/item",
                new Date(), FeedItem.UNPLAYED, feed);
        item.setImageUrl("http://example.com/item.png");
        FeedMedia media = new FeedMedia(item, "http://example.com/ep.mp3", 100, "audio/mp3");
        media.setId(55);

        MediaBrowserCompat.MediaItem mediaItem = media.getMediaItem();

        assertEquals("55", mediaItem.getMediaId());
        assertEquals("Episode Title", mediaItem.getDescription().getTitle().toString());
        assertEquals("feedTitle", mediaItem.getDescription().getDescription().toString());
        assertEquals("feedTitle", mediaItem.getDescription().getSubtitle().toString());
        assertEquals(android.net.Uri.parse("http://example.com/item.png"),
                mediaItem.getDescription().getIconUri());

        // Fallback: no item image -> feed image.
        item.setImageUrl(null);
        MediaBrowserCompat.MediaItem mediaItemFallback = media.getMediaItem();
        assertEquals(android.net.Uri.parse("http://example.com/feed.png"),
                mediaItemFallback.getDescription().getIconUri());
    }

    @Test
    public void checkEmbeddedPictureFalseWhenLocalFileUnavailable() {
        // anyFeedMedia() has no localFileUrl and downloadDate 0, so localFileAvailable() is
        // false and checkEmbeddedPicture()'s early-return branch (the only testable branch -
        // the native MediaMetadataRetrieverCompat path is conversion-only) sets hasEmbeddedPicture
        // to false without touching the native retriever.
        assertFalse(media.hasEmbeddedPicture());
    }

    private static FeedMedia parcelRoundTrip(FeedMedia original) {
        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        return FeedMedia.CREATOR.createFromParcel(parcel);
    }

    private static Boolean getHasEmbeddedPictureField(FeedMedia media) throws Exception {
        // Backing field is named hasEmbeddedPictureField in the Kotlin FeedMedia - Kotlin does
        // not allow a function (hasEmbeddedPicture()) and a property/field to share one
        // identifier, so the conversion renamed the private field to avoid the clash. The public
        // hasEmbeddedPicture() function and setHasEmbeddedPicture() are otherwise unchanged.
        Field field = FeedMedia.class.getDeclaredField("hasEmbeddedPictureField");
        field.setAccessible(true);
        return (Boolean) field.get(media);
    }

}
