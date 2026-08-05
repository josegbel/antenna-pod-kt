package de.danoeh.antennapod.net.download.serviceinterface

import android.content.Context
import android.webkit.URLUtil
import androidx.test.platform.app.InstrumentationRegistry
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.storage.preferences.UserPreferences
import java.io.File
import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang3.StringUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DownloadRequestCreatorTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        UserPreferences.init(context)
    }

    private fun createFeed(id: Long, downloadUrl: String?, title: String?): Feed {
        return Feed(id, null, title, null, null, null, null, null, null, null, null, null, downloadUrl, 0)
    }

    private fun createFeedItem(id: Long, title: String?, feed: Feed?): FeedItem {
        return FeedItem(id, title, null, null, null, FeedItem.UNPLAYED, feed)
    }

    private fun createFeedMedia(id: Long, item: FeedItem?, downloadUrl: String?, localFileUrl: String?): FeedMedia {
        return FeedMedia(id, item, 0, 0, 0, "audio/mpeg", localFileUrl, downloadUrl, 0, null, 0, 0)
    }

    @Test
    fun createFeedDeletesStaleFeedFile() {
        val feed = createFeed(1, "http://example.com/feed1.xml", "Podcast One")
        val destination = DownloadRequestCreator.create(feed).build().destination
        val staleFile = File(destination)
        assertTrue(staleFile.parentFile.isDirectory || staleFile.parentFile.mkdirs())
        assertTrue(staleFile.createNewFile())
        assertTrue(staleFile.exists())

        DownloadRequestCreator.create(feed)

        assertFalse(staleFile.exists())
    }

    @Test
    fun createFeedFilenameUsesTitleOverUrl() {
        val feed = createFeed(2, "http://example.com/feed2.xml", "Podcast Two")
        val destination = DownloadRequestCreator.create(feed).build().destination
        val expectedName = "feed-" + FileNameGenerator.generateFileName("Podcast Two") + 2
        assertEquals(expectedName, File(destination).name)
    }

    @Test
    fun createFeedFilenameFallsBackToUrlWhenTitleEmpty() {
        val feed = createFeed(3, "http://example.com/feed3.xml", "")
        val destination = DownloadRequestCreator.create(feed).build().destination
        val expectedName = "feed-" + FileNameGenerator.generateFileName("http://example.com/feed3.xml") + 3
        assertEquals(expectedName, File(destination).name)
    }

    @Test
    fun createMediaReusesExistingPartialDownload() {
        val partialFile = File.createTempFile("partial", ".mp3", context.cacheDir)
        val feed = createFeed(4, "http://example.com/feed4.xml", "Podcast Four")
        val item = createFeedItem(4, "Episode Four", feed)
        val media = createFeedMedia(4, item, "http://example.com/e4.mp3", partialFile.absolutePath)

        val request = DownloadRequestCreator.create(media).build()

        assertEquals(partialFile.absolutePath, request.destination)
        assertTrue(partialFile.exists())
    }

    @Test
    fun createMediaResolvesFilenameCollision() {
        val feed = createFeed(5, "http://example.com/feed5.xml", "Podcast Five")
        val item = createFeedItem(5, "Collision Episode", feed)
        val media = createFeedMedia(5, item, "http://example.com/e5.mp3", null)

        val destination1 = DownloadRequestCreator.create(media).build().destination
        val dest1 = File(destination1)
        assertTrue(dest1.parentFile.isDirectory || dest1.parentFile.mkdirs())
        assertTrue(dest1.createNewFile())

        val base = FilenameUtils.getBaseName(destination1)
        val ext = FilenameUtils.getExtension(destination1)

        val destination2 = DownloadRequestCreator.create(media).build().destination
        assertEquals(base + "-1." + ext, File(destination2).name)
        assertTrue(File(destination2).createNewFile())

        val destination3 = DownloadRequestCreator.create(media).build().destination
        assertEquals(base + "-2." + ext, File(destination3).name)
    }

    @Test
    fun createMediaResolvesMediafilePathFromFeedTitle() {
        val feed = createFeed(6, "http://example.com/feed6.xml", "My Podcast: Special Edition")
        val item = createFeedItem(6, "Episode Six", feed)
        val media = createFeedMedia(6, item, "http://example.com/e6.mp3", null)

        val destination = DownloadRequestCreator.create(media).build().destination
        val sanitisedTitle = FileNameGenerator.generateFileName(feed.title)

        assertTrue(destination.contains("media"))
        assertTrue(destination.contains(sanitisedTitle))
    }

    @Test
    fun createMediaFilenamePrefersTitleOverUrlGuess() {
        val feed = createFeed(7, "http://example.com/feed7.xml", "Podcast Seven")
        val item = createFeedItem(7, "Episode Seven Title", feed)
        val media = createFeedMedia(7, item, "http://example.com/e7.mp3", null)

        val destination = DownloadRequestCreator.create(media).build().destination
        val urlGuess = URLUtil.guessFileName(media.downloadUrl, null, media.mimeType)
        val expectedExt = FilenameUtils.getExtension(urlGuess)
        val expectedName = FileNameGenerator.generateFileName("Episode Seven Title") +
            "." + media.id + "." + expectedExt

        assertEquals(expectedName, File(destination).name)
    }

    @Test
    fun createMediaFilenameTruncatesLongTitleAt220Chars() {
        val longTitle = StringUtils.repeat("a", 230)
        val feed = createFeed(8, "http://example.com/feed8.xml", "Podcast Eight")
        val item = createFeedItem(8, longTitle, feed)
        val media = createFeedMedia(8, item, "http://example.com/e8.mp3", null)

        val destination = DownloadRequestCreator.create(media).build().destination
        val urlGuess = URLUtil.guessFileName(media.downloadUrl, null, media.mimeType)
        val expectedExt = FilenameUtils.getExtension(urlGuess)
        val expectedName = longTitle.substring(0, 220) + "." + media.id + "." + expectedExt

        assertEquals(expectedName, File(destination).name)
    }

    @Test
    fun createMediaWithNullDownloadUrlThrowsNpe() {
        val feed = createFeed(9, "http://example.com/feed9.xml", "Podcast Nine")
        val item = createFeedItem(9, "Episode Nine", feed)
        val media = createFeedMedia(9, item, null, null)

        assertThrows(NullPointerException::class.java) { DownloadRequestCreator.create(media) }
    }

    @Test
    fun createFeedWithNullDownloadUrlThrowsNpe() {
        val feed = createFeed(10, null, "Podcast Ten")

        assertThrows(NullPointerException::class.java) { DownloadRequestCreator.create(feed) }
    }

    @Test
    fun createMediaWithNullItemThrowsNpeInMediafilePath() {
        val media = createFeedMedia(11, null, "http://example.com/e11.mp3", null)

        assertThrows(NullPointerException::class.java) { DownloadRequestCreator.create(media) }
    }

    @Test
    fun createMediaWithNullItemAndExistingPartialFileThrowsNpe() {
        val partialFile = File.createTempFile("partial", ".mp3", context.cacheDir)
        val media = createFeedMedia(12, null, "http://example.com/e12.mp3", partialFile.absolutePath)

        assertThrows(NullPointerException::class.java) { DownloadRequestCreator.create(media) }
    }

    @Test
    fun createFeedWithNullTitleAndNullUrlThrowsNpe() {
        val feed = createFeed(13, null, null)

        assertThrows(NullPointerException::class.java) { DownloadRequestCreator.create(feed) }
    }
}
