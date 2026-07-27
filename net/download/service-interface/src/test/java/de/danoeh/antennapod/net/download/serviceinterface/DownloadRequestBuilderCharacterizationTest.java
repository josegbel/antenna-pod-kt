package de.danoeh.antennapod.net.download.serviceinterface;

import de.danoeh.antennapod.model.download.DownloadRequest;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

@RunWith(RobolectricTestRunner.class)
public class DownloadRequestBuilderCharacterizationTest {

    private static final String DEST = "file://location/media.mp3";

    private Feed createFeed(long id, String downloadUrl, String title) {
        return new Feed(id, null, title, null, null, null, null, null, null, null, null, null, downloadUrl, 0);
    }

    private FeedMedia createMedia(long id, String downloadUrl) {
        FeedItem item = new FeedItem(id, "Media Title", null, null, null, FeedItem.UNPLAYED, null);
        return new FeedMedia(id, item, 0, 0, 0, "audio/mpeg", null, downloadUrl, 0, null, 0, 0);
    }

    @Test
    public void feedConstructorSkipsPrepareUrlForLocalFeed() {
        Feed feed = createFeed(1, Feed.PREFIX_LOCAL_FOLDER + "some/path", "Local Podcast");

        DownloadRequest built = new DownloadRequestBuilder(DEST, feed).build();

        assertEquals(feed.getDownloadUrl(), built.getSource());
    }

    @Test
    public void feedConstructorAppliesPrepareUrlForNonLocalFeed() {
        Feed feed = createFeed(2, "example.com/feed2.xml", "Podcast Two");

        DownloadRequest built = new DownloadRequestBuilder(DEST, feed).build();

        assertEquals("http://example.com/feed2.xml", built.getSource());
    }

    @Test
    public void feedConstructorSetsPageNrArgument() {
        Feed feed = createFeed(3, "http://example.com/feed3.xml", "Podcast Three");
        feed.setPageNr(3);

        DownloadRequest built = new DownloadRequestBuilder(DEST, feed).build();

        assertEquals(3, built.getArguments().getInt(DownloadRequest.REQUEST_ARG_PAGE_NR));
    }

    @Test
    public void mediaConstructorAppliesPrepareUrl() {
        FeedMedia media = createMedia(4, "example.com/episode4.mp3");

        DownloadRequest built = new DownloadRequestBuilder(DEST, media).build();

        assertEquals("http://example.com/episode4.mp3", built.getSource());
    }

    @Test
    public void withInitiatedByUserFalseIsReflectedInBuiltRequest() {
        FeedMedia media = createMedia(5, "http://example.com/episode5.mp3");
        DownloadRequest built = new DownloadRequestBuilder(DEST, media).withInitiatedByUser(false).build();

        DownloadRequest ifInitiatedByUserWereTrue = new DownloadRequest(built.getDestination(), built.getSource(),
                built.getTitle(), built.getFeedfileId(), built.getFeedfileType(), built.getLastModified(),
                built.getUsername(), built.getPassword(), false, built.getArguments(), true);

        assertNotEquals(built, ifInitiatedByUserWereTrue);
    }

    @Test
    public void buildHasMediaEnqueuedFalse() {
        FeedMedia media = createMedia(6, "http://example.com/episode6.mp3");
        DownloadRequest built = new DownloadRequestBuilder(DEST, media).build();

        DownloadRequest ifMediaEnqueuedWereTrue = new DownloadRequest(built.getDestination(), built.getSource(),
                built.getTitle(), built.getFeedfileId(), built.getFeedfileType(), built.getLastModified(),
                built.getUsername(), built.getPassword(), true, built.getArguments(), true);

        assertNotEquals(built, ifMediaEnqueuedWereTrue);
    }

    @Test
    public void setSourceOverridesConstructorDerivedSource() {
        FeedMedia media = createMedia(7, "http://example.com/episode7.mp3");
        DownloadRequestBuilder builder = new DownloadRequestBuilder(DEST, media);

        builder.setSource("http://example.com/replaced.mp3");

        assertEquals("http://example.com/replaced.mp3", builder.build().getSource());
    }

    @Test
    public void setForceTrueClearsLastModified() {
        FeedMedia media = createMedia(8, "http://example.com/episode8.mp3");
        DownloadRequestBuilder builder = new DownloadRequestBuilder(DEST, media).lastModified("etag-1");

        builder.setForce(true);

        assertNull(builder.build().getLastModified());
    }

    @Test
    public void setForceFalseLeavesLastModifiedUnchanged() {
        FeedMedia media = createMedia(9, "http://example.com/episode9.mp3");
        DownloadRequestBuilder builder = new DownloadRequestBuilder(DEST, media).lastModified("etag-1");

        builder.setForce(false);

        assertEquals("etag-1", builder.build().getLastModified());
    }

    @Test
    public void lastModifiedSetsBuiltRequestsLastModified() {
        FeedMedia media = createMedia(10, "http://example.com/episode10.mp3");

        DownloadRequest built = new DownloadRequestBuilder(DEST, media).lastModified("etag-2").build();

        assertEquals("etag-2", built.getLastModified());
    }

    @Test
    public void buildWithNullSourceThrowsNpe() {
        FeedMedia media = createMedia(11, "http://example.com/episode11.mp3");
        DownloadRequestBuilder builder = new DownloadRequestBuilder(DEST, media);
        builder.setSource(null);

        assertThrows(NullPointerException.class, builder::build);
    }
}
