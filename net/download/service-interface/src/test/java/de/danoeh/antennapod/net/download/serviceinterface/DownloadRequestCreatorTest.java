package de.danoeh.antennapod.net.download.serviceinterface;

import android.content.Context;
import android.webkit.URLUtil;

import androidx.test.platform.app.InstrumentationRegistry;

import de.danoeh.antennapod.model.download.DownloadRequest;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.storage.preferences.UserPreferences;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class DownloadRequestCreatorTest {

    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        UserPreferences.init(context);
    }

    private Feed createFeed(long id, String downloadUrl, String title) {
        return new Feed(id, null, title, null, null, null, null, null, null, null, null, null, downloadUrl, 0);
    }

    private FeedItem createFeedItem(long id, String title, Feed feed) {
        return new FeedItem(id, title, null, null, null, FeedItem.UNPLAYED, feed);
    }

    private FeedMedia createFeedMedia(long id, FeedItem item, String downloadUrl, String localFileUrl) {
        return new FeedMedia(id, item, 0, 0, 0, "audio/mpeg", localFileUrl, downloadUrl, 0, null, 0, 0);
    }

    @Test
    public void createFeedDeletesStaleFeedFile() throws IOException {
        Feed feed = createFeed(1, "http://example.com/feed1.xml", "Podcast One");
        String destination = DownloadRequestCreator.create(feed).build().getDestination();
        File staleFile = new File(destination);
        assertTrue(staleFile.getParentFile().isDirectory() || staleFile.getParentFile().mkdirs());
        assertTrue(staleFile.createNewFile());
        assertTrue(staleFile.exists());

        DownloadRequestCreator.create(feed);

        assertFalse(staleFile.exists());
    }

    @Test
    public void createFeedFilenameUsesTitleOverUrl() {
        Feed feed = createFeed(2, "http://example.com/feed2.xml", "Podcast Two");
        String destination = DownloadRequestCreator.create(feed).build().getDestination();
        String expectedName = "feed-" + FileNameGenerator.generateFileName("Podcast Two") + 2;
        assertEquals(expectedName, new File(destination).getName());
    }

    @Test
    public void createFeedFilenameFallsBackToUrlWhenTitleEmpty() {
        Feed feed = createFeed(3, "http://example.com/feed3.xml", "");
        String destination = DownloadRequestCreator.create(feed).build().getDestination();
        String expectedName = "feed-" + FileNameGenerator.generateFileName("http://example.com/feed3.xml") + 3;
        assertEquals(expectedName, new File(destination).getName());
    }

    @Test
    public void createMediaReusesExistingPartialDownload() throws IOException {
        File partialFile = File.createTempFile("partial", ".mp3", context.getCacheDir());
        Feed feed = createFeed(4, "http://example.com/feed4.xml", "Podcast Four");
        FeedItem item = createFeedItem(4, "Episode Four", feed);
        FeedMedia media = createFeedMedia(4, item, "http://example.com/e4.mp3", partialFile.getAbsolutePath());

        DownloadRequest request = DownloadRequestCreator.create(media).build();

        assertEquals(partialFile.getAbsolutePath(), request.getDestination());
        assertTrue(partialFile.exists());
    }

    @Test
    public void createMediaResolvesFilenameCollision() throws IOException {
        Feed feed = createFeed(5, "http://example.com/feed5.xml", "Podcast Five");
        FeedItem item = createFeedItem(5, "Collision Episode", feed);
        FeedMedia media = createFeedMedia(5, item, "http://example.com/e5.mp3", null);

        String destination1 = DownloadRequestCreator.create(media).build().getDestination();
        File dest1 = new File(destination1);
        assertTrue(dest1.getParentFile().isDirectory() || dest1.getParentFile().mkdirs());
        assertTrue(dest1.createNewFile());

        String base = FilenameUtils.getBaseName(destination1);
        String ext = FilenameUtils.getExtension(destination1);

        String destination2 = DownloadRequestCreator.create(media).build().getDestination();
        assertEquals(base + "-1." + ext, new File(destination2).getName());
        assertTrue(new File(destination2).createNewFile());

        String destination3 = DownloadRequestCreator.create(media).build().getDestination();
        assertEquals(base + "-2." + ext, new File(destination3).getName());
    }

    @Test
    public void createMediaResolvesMediafilePathFromFeedTitle() {
        Feed feed = createFeed(6, "http://example.com/feed6.xml", "My Podcast: Special Edition");
        FeedItem item = createFeedItem(6, "Episode Six", feed);
        FeedMedia media = createFeedMedia(6, item, "http://example.com/e6.mp3", null);

        String destination = DownloadRequestCreator.create(media).build().getDestination();
        String sanitisedTitle = FileNameGenerator.generateFileName(feed.getTitle());

        assertTrue(destination.contains("media"));
        assertTrue(destination.contains(sanitisedTitle));
    }

    @Test
    public void createMediaFilenamePrefersTitleOverUrlGuess() {
        Feed feed = createFeed(7, "http://example.com/feed7.xml", "Podcast Seven");
        FeedItem item = createFeedItem(7, "Episode Seven Title", feed);
        FeedMedia media = createFeedMedia(7, item, "http://example.com/e7.mp3", null);

        String destination = DownloadRequestCreator.create(media).build().getDestination();
        String urlGuess = URLUtil.guessFileName(media.getDownloadUrl(), null, media.getMimeType());
        String expectedExt = FilenameUtils.getExtension(urlGuess);
        String expectedName = FileNameGenerator.generateFileName("Episode Seven Title")
                + "." + media.getId() + "." + expectedExt;

        assertEquals(expectedName, new File(destination).getName());
    }

    @Test
    public void createMediaFilenameTruncatesLongTitleAt220Chars() {
        String longTitle = StringUtils.repeat("a", 230);
        Feed feed = createFeed(8, "http://example.com/feed8.xml", "Podcast Eight");
        FeedItem item = createFeedItem(8, longTitle, feed);
        FeedMedia media = createFeedMedia(8, item, "http://example.com/e8.mp3", null);

        String destination = DownloadRequestCreator.create(media).build().getDestination();
        String urlGuess = URLUtil.guessFileName(media.getDownloadUrl(), null, media.getMimeType());
        String expectedExt = FilenameUtils.getExtension(urlGuess);
        String expectedName = longTitle.substring(0, 220) + "." + media.getId() + "." + expectedExt;

        assertEquals(expectedName, new File(destination).getName());
    }

    @Test
    public void createMediaWithNullDownloadUrlThrowsNpe() {
        Feed feed = createFeed(9, "http://example.com/feed9.xml", "Podcast Nine");
        FeedItem item = createFeedItem(9, "Episode Nine", feed);
        FeedMedia media = createFeedMedia(9, item, null, null);

        assertThrows(NullPointerException.class, () -> DownloadRequestCreator.create(media));
    }

    @Test
    public void createFeedWithNullDownloadUrlThrowsNpe() {
        Feed feed = createFeed(10, null, "Podcast Ten");

        assertThrows(NullPointerException.class, () -> DownloadRequestCreator.create(feed));
    }

    @Test
    public void createMediaWithNullItemThrowsNpeInMediafilePath() {
        FeedMedia media = createFeedMedia(11, null, "http://example.com/e11.mp3", null);

        assertThrows(NullPointerException.class, () -> DownloadRequestCreator.create(media));
    }

    @Test
    public void createMediaWithNullItemAndExistingPartialFileThrowsNpe() throws IOException {
        File partialFile = File.createTempFile("partial", ".mp3", context.getCacheDir());
        FeedMedia media = createFeedMedia(12, null, "http://example.com/e12.mp3", partialFile.getAbsolutePath());

        assertThrows(NullPointerException.class, () -> DownloadRequestCreator.create(media));
    }

    @Test
    public void createFeedWithNullTitleAndNullUrlThrowsNpe() {
        Feed feed = createFeed(13, null, null);

        assertThrows(NullPointerException.class, () -> DownloadRequestCreator.create(feed));
    }
}
