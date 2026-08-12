package de.danoeh.antennapod.actionbutton;

import android.content.Context;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * Test class for {@link CancelDownloadActionButton}
 */
public class CancelDownloadActionButtonTest {

    private RecordingDownloadServiceInterface recordingImpl;

    @Before
    public void setUp() {
        recordingImpl = new RecordingDownloadServiceInterface();
        DownloadServiceInterface.setImpl(recordingImpl);
    }

    @After
    public void tearDown() {
        DownloadServiceInterface.setImpl(null);
    }

    private FeedItem createFeedItem() {
        Feed feed = new Feed(0, null, "title", "http://example.com", "This is the description",
                "http://example.com/payment", "Daniel", "en", null, "http://example.com/feed",
                "http://example.com/image", null, "http://example.com/feed", System.currentTimeMillis());
        return new FeedItem(0, "Item", "ItemId", "url", new Date(), FeedItem.PLAYED, feed);
    }

    @Test
    public void nullMediaIsSkippedAndCancelIsNotCalled() {
        FeedItem item = createFeedItem();
        CancelDownloadActionButton button = new CancelDownloadActionButton(item);
        button.onClick(null);
        assertEquals(0, recordingImpl.cancelCalls.size());
    }

    @Test
    public void nonNullMediaStillReachesCancel() {
        FeedItem item = createFeedItem();
        FeedMedia media = new FeedMedia(item, "http://download.url.net/item", 1234567, "audio/mpeg");
        item.setMedia(media);
        CancelDownloadActionButton button = new CancelDownloadActionButton(item);
        try {
            button.onClick(null);
        } catch (Throwable ignored) {
            // execution continues past the recorded cancel() call into DBWriter.setFeedItem,
            // which is out of this test's interest and not this test's assertion.
        }
        assertEquals(1, recordingImpl.cancelCalls.size());
        assertSame(media, recordingImpl.cancelCalls.get(0));
    }

    private static final class RecordingDownloadServiceInterface extends DownloadServiceInterface {
        final List<FeedMedia> cancelCalls = new ArrayList<>();

        @Override
        public void downloadNow(Context context, FeedItem item, boolean ignoreConstraints) {
        }

        @Override
        public void download(Context context, FeedItem item) {
        }

        @Override
        public void cancel(Context context, FeedMedia media) {
            cancelCalls.add(media);
        }

        @Override
        public void cancelAll(Context context) {
        }

        @Override
        public int getNumberOfActiveDownloads(Context context) {
            return 0;
        }
    }
}
