package de.danoeh.antennapod.net.download.serviceinterface

import android.content.Context
import de.danoeh.antennapod.model.download.DownloadStatus
import de.danoeh.antennapod.model.feed.Feed
import java.util.HashMap
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DownloadServiceInterfaceTest {

    @Before
    @After
    fun resetStatics() {
        DownloadServiceInterface.setImpl(null)
        FeedUpdateManager.setInstance(null)
        AutoDownloadManager.setInstance(null)
    }

    @Test
    fun testIsDownloadingEpisodeUrlAbsentReturnsFalse() {
        val dsi = DownloadServiceInterfaceStub()
        assertFalse(dsi.isDownloadingEpisode("http://example.com/episode.mp3"))
        assertFalse(dsi.isEpisodeQueued("http://example.com/episode.mp3"))
        assertEquals(-1, dsi.getProgress("http://example.com/episode.mp3"))
    }

    @Test
    fun testStateCompletedIsNotDownloading() {
        val dsi = DownloadServiceInterfaceStub()
        val url = "http://example.com/episode.mp3"
        val downloads = HashMap<String?, DownloadStatus>()
        downloads.put(url, DownloadStatus(DownloadStatus.STATE_COMPLETED, 100))
        dsi.setCurrentDownloads(downloads)

        assertFalse(dsi.isDownloadingEpisode(url))
        assertFalse(dsi.isEpisodeQueued(url))
        assertEquals(-1, dsi.getProgress(url))
    }

    @Test
    fun testStateQueuedIsQueuedAndDownloading() {
        val dsi = DownloadServiceInterfaceStub()
        val url = "http://example.com/episode.mp3"
        val downloads = HashMap<String?, DownloadStatus>()
        downloads.put(url, DownloadStatus(DownloadStatus.STATE_QUEUED, 0))
        dsi.setCurrentDownloads(downloads)

        assertTrue(dsi.isDownloadingEpisode(url))
        assertTrue(dsi.isEpisodeQueued(url))
        assertEquals(0, dsi.getProgress(url))
    }

    @Test
    fun testStateRunningIsDownloadingNotQueued() {
        val dsi = DownloadServiceInterfaceStub()
        val url = "http://example.com/episode.mp3"
        val downloads = HashMap<String?, DownloadStatus>()
        downloads.put(url, DownloadStatus(DownloadStatus.STATE_RUNNING, 42))
        dsi.setCurrentDownloads(downloads)

        assertTrue(dsi.isDownloadingEpisode(url))
        assertFalse(dsi.isEpisodeQueued(url))
        assertEquals(42, dsi.getProgress(url))
    }

    @Test
    fun testNullUrlIsSafe() {
        val dsi = DownloadServiceInterfaceStub()
        val downloads = HashMap<String?, DownloadStatus>()
        downloads.put("http://example.com/episode.mp3", DownloadStatus(DownloadStatus.STATE_QUEUED, 0))
        dsi.setCurrentDownloads(downloads)

        assertFalse(dsi.isDownloadingEpisode(null))
        assertFalse(dsi.isEpisodeQueued(null))
        assertEquals(-1, dsi.getProgress(null))
    }

    @Test
    fun testSetCurrentDownloadsReplacesRatherThanMerges() {
        val dsi = DownloadServiceInterfaceStub()
        val first = HashMap<String?, DownloadStatus>()
        first.put("a", DownloadStatus(DownloadStatus.STATE_QUEUED, 0))
        dsi.setCurrentDownloads(first)
        assertTrue(dsi.isDownloadingEpisode("a"))

        val second = HashMap<String?, DownloadStatus>()
        second.put("b", DownloadStatus(DownloadStatus.STATE_QUEUED, 0))
        dsi.setCurrentDownloads(second)

        assertFalse(dsi.isDownloadingEpisode("a"))
        assertTrue(dsi.isDownloadingEpisode("b"))
    }

    @Test
    fun testSetCurrentDownloadsAliasesTheInstalledMap() {
        val dsi = DownloadServiceInterfaceStub()
        val downloads = HashMap<String?, DownloadStatus>()
        dsi.setCurrentDownloads(downloads)
        assertFalse(dsi.isDownloadingEpisode("x"))

        downloads.put("x", DownloadStatus(DownloadStatus.STATE_QUEUED, 0))

        assertTrue(dsi.isDownloadingEpisode("x"))
    }

    @Test
    fun testWorkConstants() {
        assertEquals("episodeDownload", DownloadServiceInterface.WORK_TAG)
        assertEquals("episodeUrl:", DownloadServiceInterface.WORK_TAG_EPISODE_URL)
        assertEquals("progress", DownloadServiceInterface.WORK_DATA_PROGRESS)
        assertEquals("media_id", DownloadServiceInterface.WORK_DATA_MEDIA_ID)
        assertEquals("was_queued", DownloadServiceInterface.WORK_DATA_WAS_QUEUED)
    }

    @Test
    fun testDownloadServiceInterfaceGetSetImplRoundTrip() {
        assertNull(DownloadServiceInterface.get())

        val stub = DownloadServiceInterfaceStub()
        DownloadServiceInterface.setImpl(stub)
        assertSame(stub, DownloadServiceInterface.get())
    }

    @Test
    fun testFeedUpdateManagerGetSetInstanceRoundTrip() {
        assertNull(FeedUpdateManager.getInstance())

        val manager: FeedUpdateManager = object : FeedUpdateManager() {
            override fun restartUpdateAlarm(context: Context?, replace: Boolean) {
            }

            override fun runOnce(context: Context?) {
            }

            override fun runOnce(context: Context?, feed: Feed?) {
            }

            override fun runOnce(context: Context?, feed: Feed?, nextPage: Boolean) {
            }

            override fun runOnceOrAsk(context: Context) {
            }

            override fun runOnceOrAsk(context: Context, feed: Feed?) {
            }
        }
        FeedUpdateManager.setInstance(manager)
        assertSame(manager, FeedUpdateManager.getInstance())
    }

    @Test
    fun testAutoDownloadManagerGetSetInstanceRoundTrip() {
        assertNull(AutoDownloadManager.getInstance())

        val manager: AutoDownloadManager = object : AutoDownloadManager() {
            override fun autodownloadUndownloadedItems(context: Context?): Future<*> = FutureTask<Void?> { null }

            override fun performAutoCleanup(context: Context?) {
            }
        }
        AutoDownloadManager.setInstance(manager)
        assertSame(manager, AutoDownloadManager.getInstance())
    }
}
