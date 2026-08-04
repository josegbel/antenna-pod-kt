package de.danoeh.antennapod.net.download.serviceinterface

import de.danoeh.antennapod.model.download.DownloadRequest
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DownloadRequestBuilderCharacterizationTest {

    private fun createFeed(id: Long, downloadUrl: String?, title: String?): Feed {
        return Feed(id, null, title, null, null, null, null, null, null, null, null, null, downloadUrl, 0)
    }

    private fun createMedia(id: Long, downloadUrl: String?): FeedMedia {
        val item = FeedItem(id, "Media Title", null, null, null, FeedItem.UNPLAYED, null)
        return FeedMedia(id, item, 0, 0, 0, "audio/mpeg", null, downloadUrl, 0, null, 0, 0)
    }

    @Test
    fun feedConstructorSkipsPrepareUrlForLocalFeed() {
        val feed = createFeed(1, Feed.PREFIX_LOCAL_FOLDER + "some/path", "Local Podcast")

        val built = DownloadRequestBuilder(DEST, feed).build()

        assertEquals(feed.downloadUrl, built.source)
    }

    @Test
    fun feedConstructorAppliesPrepareUrlForNonLocalFeed() {
        val feed = createFeed(2, "example.com/feed2.xml", "Podcast Two")

        val built = DownloadRequestBuilder(DEST, feed).build()

        assertEquals("http://example.com/feed2.xml", built.source)
    }

    @Test
    fun feedConstructorSetsPageNrArgument() {
        val feed = createFeed(3, "http://example.com/feed3.xml", "Podcast Three")
        feed.pageNr = 3

        val built = DownloadRequestBuilder(DEST, feed).build()

        assertEquals(3, built.arguments.getInt(DownloadRequest.REQUEST_ARG_PAGE_NR))
    }

    @Test
    fun mediaConstructorAppliesPrepareUrl() {
        val media = createMedia(4, "example.com/episode4.mp3")

        val built = DownloadRequestBuilder(DEST, media).build()

        assertEquals("http://example.com/episode4.mp3", built.source)
    }

    @Test
    fun withInitiatedByUserFalseIsReflectedInBuiltRequest() {
        val media = createMedia(5, "http://example.com/episode5.mp3")
        val built = DownloadRequestBuilder(DEST, media).withInitiatedByUser(false).build()

        val ifInitiatedByUserWereTrue = DownloadRequest(
            built.destination, built.source,
            built.title, built.feedfileId, built.feedfileType, built.lastModified,
            built.username, built.password, false, built.arguments, true
        )

        assertNotEquals(built, ifInitiatedByUserWereTrue)
    }

    @Test
    fun buildHasMediaEnqueuedFalse() {
        val media = createMedia(6, "http://example.com/episode6.mp3")
        val built = DownloadRequestBuilder(DEST, media).build()

        val ifMediaEnqueuedWereTrue = DownloadRequest(
            built.destination, built.source,
            built.title, built.feedfileId, built.feedfileType, built.lastModified,
            built.username, built.password, true, built.arguments, true
        )

        assertNotEquals(built, ifMediaEnqueuedWereTrue)
    }

    @Test
    fun setSourceOverridesConstructorDerivedSource() {
        val media = createMedia(7, "http://example.com/episode7.mp3")
        val builder = DownloadRequestBuilder(DEST, media)

        builder.setSource("http://example.com/replaced.mp3")

        assertEquals("http://example.com/replaced.mp3", builder.build().source)
    }

    @Test
    fun setForceTrueClearsLastModified() {
        val media = createMedia(8, "http://example.com/episode8.mp3")
        val builder = DownloadRequestBuilder(DEST, media).lastModified("etag-1")

        builder.setForce(true)

        assertNull(builder.build().lastModified)
    }

    @Test
    fun setForceFalseLeavesLastModifiedUnchanged() {
        val media = createMedia(9, "http://example.com/episode9.mp3")
        val builder = DownloadRequestBuilder(DEST, media).lastModified("etag-1")

        builder.setForce(false)

        assertEquals("etag-1", builder.build().lastModified)
    }

    @Test
    fun lastModifiedSetsBuiltRequestsLastModified() {
        val media = createMedia(10, "http://example.com/episode10.mp3")

        val built = DownloadRequestBuilder(DEST, media).lastModified("etag-2").build()

        assertEquals("etag-2", built.lastModified)
    }

    @Test
    fun buildWithNullSourceThrowsNpe() {
        val media = createMedia(11, "http://example.com/episode11.mp3")
        val builder = DownloadRequestBuilder(DEST, media)
        builder.setSource(null)

        assertThrows(NullPointerException::class.java, builder::build)
    }

    private companion object {
        private const val DEST = "file://location/media.mp3"
    }
}
