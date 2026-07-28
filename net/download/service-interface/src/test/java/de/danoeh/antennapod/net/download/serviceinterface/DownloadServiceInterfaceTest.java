package de.danoeh.antennapod.net.download.serviceinterface;

import android.content.Context;

import de.danoeh.antennapod.model.download.DownloadStatus;
import de.danoeh.antennapod.model.feed.Feed;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class DownloadServiceInterfaceTest {

    @Before
    @After
    public void resetStatics() {
        DownloadServiceInterface.setImpl(null);
        FeedUpdateManager.setInstance(null);
        AutoDownloadManager.setInstance(null);
    }

    @Test
    public void testIsDownloadingEpisodeUrlAbsentReturnsFalse() {
        DownloadServiceInterface dsi = new DownloadServiceInterfaceStub();
        assertFalse(dsi.isDownloadingEpisode("http://example.com/episode.mp3"));
        assertFalse(dsi.isEpisodeQueued("http://example.com/episode.mp3"));
        assertEquals(-1, dsi.getProgress("http://example.com/episode.mp3"));
    }

    @Test
    public void testStateCompletedIsNotDownloading() {
        DownloadServiceInterface dsi = new DownloadServiceInterfaceStub();
        String url = "http://example.com/episode.mp3";
        Map<String, DownloadStatus> downloads = new HashMap<>();
        downloads.put(url, new DownloadStatus(DownloadStatus.STATE_COMPLETED, 100));
        dsi.setCurrentDownloads(downloads);

        assertFalse(dsi.isDownloadingEpisode(url));
        assertFalse(dsi.isEpisodeQueued(url));
        assertEquals(-1, dsi.getProgress(url));
    }

    @Test
    public void testStateQueuedIsQueuedAndDownloading() {
        DownloadServiceInterface dsi = new DownloadServiceInterfaceStub();
        String url = "http://example.com/episode.mp3";
        Map<String, DownloadStatus> downloads = new HashMap<>();
        downloads.put(url, new DownloadStatus(DownloadStatus.STATE_QUEUED, 0));
        dsi.setCurrentDownloads(downloads);

        assertTrue(dsi.isDownloadingEpisode(url));
        assertTrue(dsi.isEpisodeQueued(url));
        assertEquals(0, dsi.getProgress(url));
    }

    @Test
    public void testStateRunningIsDownloadingNotQueued() {
        DownloadServiceInterface dsi = new DownloadServiceInterfaceStub();
        String url = "http://example.com/episode.mp3";
        Map<String, DownloadStatus> downloads = new HashMap<>();
        downloads.put(url, new DownloadStatus(DownloadStatus.STATE_RUNNING, 42));
        dsi.setCurrentDownloads(downloads);

        assertTrue(dsi.isDownloadingEpisode(url));
        assertFalse(dsi.isEpisodeQueued(url));
        assertEquals(42, dsi.getProgress(url));
    }

    @Test
    public void testNullUrlIsSafe() {
        DownloadServiceInterface dsi = new DownloadServiceInterfaceStub();
        Map<String, DownloadStatus> downloads = new HashMap<>();
        downloads.put("http://example.com/episode.mp3", new DownloadStatus(DownloadStatus.STATE_QUEUED, 0));
        dsi.setCurrentDownloads(downloads);

        assertFalse(dsi.isDownloadingEpisode(null));
        assertFalse(dsi.isEpisodeQueued(null));
        assertEquals(-1, dsi.getProgress(null));
    }

    @Test
    public void testSetCurrentDownloadsReplacesRatherThanMerges() {
        DownloadServiceInterface dsi = new DownloadServiceInterfaceStub();
        Map<String, DownloadStatus> first = new HashMap<>();
        first.put("a", new DownloadStatus(DownloadStatus.STATE_QUEUED, 0));
        dsi.setCurrentDownloads(first);
        assertTrue(dsi.isDownloadingEpisode("a"));

        Map<String, DownloadStatus> second = new HashMap<>();
        second.put("b", new DownloadStatus(DownloadStatus.STATE_QUEUED, 0));
        dsi.setCurrentDownloads(second);

        assertFalse(dsi.isDownloadingEpisode("a"));
        assertTrue(dsi.isDownloadingEpisode("b"));
    }

    @Test
    public void testSetCurrentDownloadsAliasesTheInstalledMap() {
        DownloadServiceInterface dsi = new DownloadServiceInterfaceStub();
        Map<String, DownloadStatus> downloads = new HashMap<>();
        dsi.setCurrentDownloads(downloads);
        assertFalse(dsi.isDownloadingEpisode("x"));

        downloads.put("x", new DownloadStatus(DownloadStatus.STATE_QUEUED, 0));

        assertTrue(dsi.isDownloadingEpisode("x"));
    }

    @Test
    public void testWorkConstants() {
        assertEquals("episodeDownload", DownloadServiceInterface.WORK_TAG);
        assertEquals("episodeUrl:", DownloadServiceInterface.WORK_TAG_EPISODE_URL);
        assertEquals("progress", DownloadServiceInterface.WORK_DATA_PROGRESS);
        assertEquals("media_id", DownloadServiceInterface.WORK_DATA_MEDIA_ID);
        assertEquals("was_queued", DownloadServiceInterface.WORK_DATA_WAS_QUEUED);
    }

    @Test
    public void testDownloadServiceInterfaceGetSetImplRoundTrip() {
        assertNull(DownloadServiceInterface.get());

        DownloadServiceInterfaceStub stub = new DownloadServiceInterfaceStub();
        DownloadServiceInterface.setImpl(stub);
        assertSame(stub, DownloadServiceInterface.get());
    }

    @Test
    public void testFeedUpdateManagerGetSetInstanceRoundTrip() {
        assertNull(FeedUpdateManager.getInstance());

        FeedUpdateManager manager = new FeedUpdateManager() {
            @Override
            public void restartUpdateAlarm(Context context, boolean replace) {
            }

            @Override
            public void runOnce(Context context) {
            }

            @Override
            public void runOnce(Context context, Feed feed) {
            }

            @Override
            public void runOnce(Context context, Feed feed, boolean nextPage) {
            }

            @Override
            public void runOnceOrAsk(Context context) {
            }

            @Override
            public void runOnceOrAsk(Context context, Feed feed) {
            }
        };
        FeedUpdateManager.setInstance(manager);
        assertSame(manager, FeedUpdateManager.getInstance());
    }

    @Test
    public void testAutoDownloadManagerGetSetInstanceRoundTrip() {
        assertNull(AutoDownloadManager.getInstance());

        AutoDownloadManager manager = new AutoDownloadManager() {
            @Override
            public Future<?> autodownloadUndownloadedItems(Context context) {
                return null;
            }

            @Override
            public void performAutoCleanup(Context context) {
            }
        };
        AutoDownloadManager.setInstance(manager);
        assertSame(manager, AutoDownloadManager.getInstance());
    }
}
