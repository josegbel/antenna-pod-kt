package de.danoeh.antennapod.model.feed

import android.net.Uri
import android.os.Parcel
import de.danoeh.antennapod.model.feed.FeedMediaMother.anyFeedMedia
import de.danoeh.antennapod.model.playback.RemoteMedia
import java.lang.reflect.Field
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner

// Runs under Robolectric (this milestone's disclosed one-milestone exception to :model's
// Robolectric-free precedent) - needed for real android.os.Parcel round trips and for
// FeedMedia.getMediaItem()'s android.net.Uri.parse/MediaBrowserCompat bridge.
@RunWith(RobolectricTestRunner::class)
class FeedMediaTest {

    private lateinit var media: FeedMedia

    @Before
    fun setUp() {
        media = anyFeedMedia()
    }

    /**
     * Downloading a media from a not new and not played item should not change the item state.
     */
    @Test
    fun testDownloadMediaOfNotNewAndNotPlayedItem_unchangedItemState() {
        val item = mock(FeedItem::class.java)
        `when`(item.isNew).thenReturn(false)
        `when`(item.isPlayed).thenReturn(false)

        media.item = item
        media.setDownloaded(true, System.currentTimeMillis())

        verify(item, never()).setNew()
        verify(item, never()).isPlayed = true
        verify(item, never()).isPlayed = false
    }

    /**
     * Downloading a media from a played item (thus not new) should not change the item state.
     */
    @Test
    fun testDownloadMediaOfPlayedItem_unchangedItemState() {
        val item = mock(FeedItem::class.java)
        `when`(item.isNew).thenReturn(false)
        `when`(item.isPlayed).thenReturn(true)

        media.item = item
        media.setDownloaded(true, System.currentTimeMillis())

        verify(item, never()).setNew()
        verify(item, never()).isPlayed = true
        verify(item, never()).isPlayed = false
    }

    /**
     * Downloading a media from a new item (thus not played) should change the item to not played.
     */
    @Test
    fun testDownloadMediaOfNewItem_changedToNotPlayedItem() {
        val item = mock(FeedItem::class.java)
        `when`(item.isNew).thenReturn(true)
        `when`(item.isPlayed).thenReturn(false)

        media.item = item
        media.setDownloaded(true, System.currentTimeMillis())

        verify(item).isPlayed = false
        verify(item, never()).setNew()
        verify(item, never()).isPlayed = true
    }

    @Test
    fun parcelRoundTripDropsItemKeepsItemIdAndConvertsNullDateToEpoch() {
        val feed = Feed("http://example.com/feed", null, "feedTitle")
        val item = FeedItem(
            777,
            "title",
            "item-id",
            "http://example.com/item",
            Date(),
            FeedItem.UNPLAYED,
            feed
        )

        val original = FeedMedia(
            42, item, 1000, 500, 2000, "audio/mp3", "/local/file.mp3",
            "http://example.com/ep.mp3", 123456789L, null, 1000, 999L
        )
        original.playedDuration = 5000
        // Pre-condition: playedDurationWhenStarted (set at construction to the original
        // playedDuration, 1000) genuinely diverges from the now-advanced playedDuration (5000),
        // so the round trip's asymmetry below is actually discriminated, not coincidentally
        // matching.
        assertNotEquals(original.playedDuration, original.playedDurationWhenStarted)

        val restored = parcelRoundTrip(original)

        assertNull(restored.item)
        assertEquals(item.id, restored.itemId)
        assertEquals(Date(0), restored.lastPlayedTimeHistory)
        // Not parceled - field-initializer default, NOT 0.
        assertEquals(-1, restored.startPosition)
        // Not parceled - but the 12-arg constructor the CREATOR calls unconditionally sets
        // playedDurationWhenStarted = playedDuration, so it tracks the restored (parceled)
        // playedDuration, not the original's pre-parcel snapshot (1000).
        assertEquals(5000, restored.playedDuration)
        assertEquals(restored.playedDuration, restored.playedDurationWhenStarted)
        // Not parceled - the 12-arg constructor never sets it (only the 13-arg overload does),
        // so it restores to its unset null ("unknown, will be checked"). No public getter reads
        // the raw field without triggering computation, so reflection pins the exact state.
        assertNull(getHasEmbeddedPictureField(restored))
    }

    @Test
    fun equalsIsIdOnly() {
        val a = anyFeedMedia()
        a.id = 5
        val b = FeedMedia(null, "http://example.com/other.mp3", 999, "audio/ogg")
        b.id = 5
        assertEquals(a, b)

        val c = anyFeedMedia()
        c.id = 6
        assertNotEquals(a, c)
    }

    @Test
    fun feedMediaEqualsRemoteMediaDelegatesSymmetrically() {
        val feed = Feed("http://example.com/feed", null, "feedTitle")
        val item = FeedItem(
            1,
            "title",
            "item-id-1",
            "http://example.com/item",
            Date(),
            FeedItem.UNPLAYED,
            feed
        )
        val fm = FeedMedia(item, "http://example.com/ep.mp3", 100, "audio/mp3")
        fm.id = 42

        val rm = RemoteMedia(
            "http://example.com/ep.mp3", "item-id-1", "http://example.com/feed",
            "feedTitle", "title", "http://example.com/item", "author", "image", "feedlink",
            "audio/mp3", Date(), "notes"
        )

        assertTrue(fm.equals(rm))
        assertTrue(rm.equals(fm))
    }

    @Test
    fun getMediaItemMapsFieldsAndIconUri() {
        val feed = Feed("http://example.com/feed", null, "feedTitle")
        feed.imageUrl = "http://example.com/feed.png"
        val item = FeedItem(
            10,
            "Episode Title",
            "item-id",
            "http://example.com/item",
            Date(),
            FeedItem.UNPLAYED,
            feed
        )
        item.imageUrl = "http://example.com/item.png"
        val media = FeedMedia(item, "http://example.com/ep.mp3", 100, "audio/mp3")
        media.id = 55

        val mediaItem = media.getMediaItem()

        assertEquals("55", mediaItem.mediaId)
        assertEquals("Episode Title", mediaItem.description.title.toString())
        assertEquals("feedTitle", mediaItem.description.description.toString())
        assertEquals("feedTitle", mediaItem.description.subtitle.toString())
        assertEquals(
            Uri.parse("http://example.com/item.png"),
            mediaItem.description.iconUri
        )

        // Fallback: no item image -> feed image.
        item.imageUrl = null
        val mediaItemFallback = media.getMediaItem()
        assertEquals(
            Uri.parse("http://example.com/feed.png"),
            mediaItemFallback.description.iconUri
        )
    }

    @Test
    fun checkEmbeddedPictureFalseWhenLocalFileUnavailable() {
        // anyFeedMedia() has no localFileUrl and downloadDate 0, so localFileAvailable() is
        // false and checkEmbeddedPicture()'s early-return branch (the only testable branch -
        // the native MediaMetadataRetrieverCompat path is conversion-only) sets hasEmbeddedPicture
        // to false without touching the native retriever.
        assertFalse(media.hasEmbeddedPicture())
    }

    private fun parcelRoundTrip(original: FeedMedia): FeedMedia {
        val parcel = Parcel.obtain()
        original.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        return FeedMedia.CREATOR.createFromParcel(parcel)
    }

    private fun getHasEmbeddedPictureField(media: FeedMedia): Boolean? {
        // Backing field is named hasEmbeddedPictureField in the Kotlin FeedMedia - Kotlin does
        // not allow a function (hasEmbeddedPicture()) and a property/field to share one
        // identifier, so the conversion renamed the private field to avoid the clash. The public
        // hasEmbeddedPicture() function and setHasEmbeddedPicture() are otherwise unchanged.
        val field: Field = FeedMedia::class.java.getDeclaredField("hasEmbeddedPictureField")
        field.isAccessible = true
        return field.get(media) as Boolean?
    }
}
