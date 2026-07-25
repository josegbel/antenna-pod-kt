package de.danoeh.antennapod.model.playback

import android.os.Parcel
import de.danoeh.antennapod.model.feed.Chapter
import java.util.ArrayList
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Characterizes RemoteMedia's Parcelable/equals-hashCode behavior against the live Java
// implementation, under Robolectric (this milestone's disclosed one-milestone exception to
// :model's Robolectric-free precedent).
@RunWith(RobolectricTestRunner::class)
class RemoteMediaTest {

    @Test
    fun parcelRoundTripDropsChaptersAndConvertsNullPubDateToEpoch() {
        val original = RemoteMedia(
            "http://example.com/ep.mp3", "item-1", "http://example.com/feed",
            "feedTitle", "episodeTitle", "http://example.com/link", "author",
            "http://example.com/image.png", "http://example.com/feedlink", "audio/mp3",
            null, "notes"
        )
        val chapters: MutableList<Chapter> = ArrayList()
        chapters.add(Chapter(0, "chapter1", "http://example.com/1", null))
        original.chapters = chapters

        val restored = parcelRoundTrip(original)

        assertNull(restored.chapters)
        assertEquals(Date(0), restored.pubDate)
    }

    @Test
    fun parcelRoundTripPreservesFieldSubset() {
        val original = RemoteMedia(
            "http://example.com/ep.mp3", "item-1", "http://example.com/feed",
            "feedTitle", "episodeTitle", "http://example.com/link", "author",
            "http://example.com/image.png", "http://example.com/feedlink", "audio/mp3",
            Date(123456789L), "notes"
        )
        original.duration = 1000
        original.position = 500
        original.lastPlayedTimeStatistics = 999L

        val restored = parcelRoundTrip(original)

        assertEquals(original.downloadUrl, restored.downloadUrl)
        assertEquals(original.getEpisodeIdentifier(), restored.getEpisodeIdentifier())
        assertEquals(original.feedUrl, restored.feedUrl)
        assertEquals(original.feedTitle, restored.feedTitle)
        assertEquals(original.episodeTitle, restored.episodeTitle)
        assertEquals(original.episodeLink, restored.episodeLink)
        assertEquals(original.feedAuthor, restored.feedAuthor)
        assertEquals(original.imageUrl, restored.imageUrl)
        assertEquals(original.feedLink, restored.feedLink)
        assertEquals(original.mimeType, restored.mimeType)
        assertEquals(original.pubDate, restored.pubDate)
        assertEquals(original.notes, restored.notes)
        assertEquals(original.duration, restored.duration)
        assertEquals(original.position, restored.position)
        assertEquals(original.lastPlayedTimeStatistics, restored.lastPlayedTimeStatistics)
    }

    @Test
    fun equalsAndHashCodeOverThreeFieldsOnly() {
        val a = RemoteMedia(
            "http://example.com/ep.mp3", "item-1", "http://example.com/feed",
            "feedTitleA", "episodeTitleA", "linkA", "authorA", "imageA", "feedLinkA", "audio/mp3",
            Date(1L), "notesA"
        )
        val b = RemoteMedia(
            "http://example.com/ep.mp3", "item-1", "http://example.com/feed",
            "feedTitleB", "episodeTitleB", "linkB", "authorB", "imageB", "feedLinkB", "audio/ogg",
            Date(2L), "notesB"
        )

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())

        val differentDownloadUrl = RemoteMedia(
            "http://example.com/other.mp3", "item-1",
            "http://example.com/feed", "feedTitleA", "episodeTitleA", "linkA", "authorA", "imageA",
            "feedLinkA", "audio/mp3", Date(1L), "notesA"
        )
        assertNotEquals(a, differentDownloadUrl)
    }

    private fun parcelRoundTrip(original: RemoteMedia): RemoteMedia {
        val parcel = Parcel.obtain()
        original.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        return RemoteMedia.CREATOR.createFromParcel(parcel)
    }
}
