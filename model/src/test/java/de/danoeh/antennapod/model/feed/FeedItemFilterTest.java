package de.danoeh.antennapod.model.feed;

import org.junit.Test;

import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class FeedItemFilterTest {

    @Test
    public void testConstructorFromSingleCsvString() {
        FeedItemFilter filter = new FeedItemFilter("played,queued");

        assertTrue(filter.showPlayed);
        assertTrue(filter.showQueued);
        assertFalse(filter.showUnplayed);
        assertFalse(filter.showDownloaded);
    }

    @Test
    public void testConstructorFromVarargs() {
        FeedItemFilter filter = new FeedItemFilter("played", "queued");

        assertTrue(filter.showPlayed);
        assertTrue(filter.showQueued);
        assertFalse(filter.showUnplayed);
        assertFalse(filter.showDownloaded);
    }

    @Test
    public void testConstructorFromFilterPlusAdditionalProperties() {
        FeedItemFilter base = new FeedItemFilter("played");
        FeedItemFilter extended = new FeedItemFilter(base, "queued");

        assertTrue(extended.showPlayed);
        assertTrue(extended.showQueued);
    }

    @Test
    public void testConstructorFromFilterWithZeroAdditionalPropertiesYieldsNoTrailingEmptyEntry() {
        // Mirrors the real Feed.java call site `new FeedItemFilter(filter)`: the
        // (FeedItemFilter, String...) constructor joins the base filter's values plus a trailing
        // comma even when 0 additionalProperties are passed, and the resulting properties array
        // must not contain a phantom "" entry from that trailing comma (Java's String.split with
        // the default limit of 0 strips trailing empty strings; this must be preserved exactly).
        FeedItemFilter base = new FeedItemFilter("played");

        FeedItemFilter copy = new FeedItemFilter(base);

        assertEquals(1, copy.getValues().length);
        assertEquals("played", copy.getValues()[0]);
        assertTrue(copy.showPlayed);
    }

    @Test
    public void testUnfiltered() {
        FeedItemFilter filter = FeedItemFilter.unfiltered();

        assertEquals(0, filter.getValues().length);
        assertFalse(filter.showPlayed);
        assertFalse(filter.showUnplayed);
        assertFalse(filter.includeSubscribed);
    }

    @Test
    public void testEmptyStringYieldsZeroLengthProperties() {
        FeedItemFilter filter = new FeedItemFilter("");

        assertEquals(0, filter.getValues().length);
    }

    @Test
    public void testWithoutRemovesExactlyThatPropertyAndNoOther() {
        FeedItemFilter filter = new FeedItemFilter("played,queued,downloaded");

        FeedItemFilter result = filter.without(FeedItemFilter.QUEUED);

        List<String> values = result.getValuesList();
        assertEquals(2, values.size());
        assertTrue(values.contains(FeedItemFilter.PLAYED));
        assertTrue(values.contains(FeedItemFilter.DOWNLOADED));
        assertFalse(values.contains(FeedItemFilter.QUEUED));
    }

    @Test
    public void testGetValuesReturnsDefensiveClone() {
        FeedItemFilter filter = new FeedItemFilter("played");

        String[] values = filter.getValues();
        values[0] = "hacked";

        assertEquals("played", filter.getValues()[0]);
    }

    @Test
    public void testGetValuesListDoesNotDefensivelyCopy() {
        // Pinning existing behavior: getValuesList() wraps the backing array directly
        // (Arrays.asList), unlike getValues() which clones. A write through the list
        // mutates state visible to subsequent calls - this is not a bug to fix here.
        FeedItemFilter filter = new FeedItemFilter("played");

        List<String> values = filter.getValuesList();
        values.set(0, "unplayed");

        assertEquals("unplayed", filter.getValuesList().get(0));
        assertEquals("unplayed", filter.getValues()[0]);
    }

    @Test
    public void testMatchesShowPlayedAndShowUnplayed() {
        FeedItem playedItem = new FeedItem();
        playedItem.setPlayState(FeedItem.PLAYED);
        FeedItem unplayedItem = new FeedItem();
        unplayedItem.setPlayState(FeedItem.UNPLAYED);

        FeedItemFilter showPlayed = new FeedItemFilter(FeedItemFilter.PLAYED);
        assertTrue(showPlayed.matches(playedItem));
        assertFalse(showPlayed.matches(unplayedItem));

        FeedItemFilter showUnplayed = new FeedItemFilter(FeedItemFilter.UNPLAYED);
        assertTrue(showUnplayed.matches(unplayedItem));
        assertFalse(showUnplayed.matches(playedItem));
    }

    @Test
    public void testMatchesShowNew() {
        FeedItem newItem = new FeedItem();
        newItem.setPlayState(FeedItem.NEW);
        FeedItem oldItem = new FeedItem();
        oldItem.setPlayState(FeedItem.UNPLAYED);

        FeedItemFilter filter = new FeedItemFilter(FeedItemFilter.NEW);

        assertTrue(filter.matches(newItem));
        assertFalse(filter.matches(oldItem));
    }

    @Test
    public void testMatchesShowPausedAndShowNotPaused() {
        FeedItem inProgressItem = new FeedItem();
        FeedMedia inProgressMedia = new FeedMedia(inProgressItem, "http://example.com/a.mp3", 1, "audio/mp3");
        inProgressMedia.setPosition(1000);
        inProgressItem.setMedia(inProgressMedia);

        FeedItem freshItem = new FeedItem();
        FeedMedia freshMedia = new FeedMedia(freshItem, "http://example.com/b.mp3", 1, "audio/mp3");
        freshItem.setMedia(freshMedia);

        FeedItemFilter showPaused = new FeedItemFilter(FeedItemFilter.PAUSED);
        assertTrue(showPaused.matches(inProgressItem));
        assertFalse(showPaused.matches(freshItem));

        FeedItemFilter showNotPaused = new FeedItemFilter(FeedItemFilter.NOT_PAUSED);
        assertTrue(showNotPaused.matches(freshItem));
        assertFalse(showNotPaused.matches(inProgressItem));
    }

    @Test
    public void testMatchesShowQueuedAndShowNotQueued() {
        FeedItem queuedItem = new FeedItem();
        queuedItem.addTag(FeedItem.TAG_QUEUE);
        FeedItem notQueuedItem = new FeedItem();

        FeedItemFilter showQueued = new FeedItemFilter(FeedItemFilter.QUEUED);
        assertTrue(showQueued.matches(queuedItem));
        assertFalse(showQueued.matches(notQueuedItem));

        FeedItemFilter showNotQueued = new FeedItemFilter(FeedItemFilter.NOT_QUEUED);
        assertTrue(showNotQueued.matches(notQueuedItem));
        assertFalse(showNotQueued.matches(queuedItem));
    }

    @Test
    public void testMatchesShowDownloadedAndShowNotDownloaded() {
        FeedItem downloadedItem = new FeedItem();
        FeedMedia downloadedMedia = new FeedMedia(downloadedItem, "http://example.com/a.mp3", 1, "audio/mp3");
        downloadedMedia.setDownloaded(true, System.currentTimeMillis());
        downloadedItem.setMedia(downloadedMedia);

        FeedItem notDownloadedItem = new FeedItem();
        FeedMedia notDownloadedMedia = new FeedMedia(notDownloadedItem, "http://example.com/b.mp3", 1, "audio/mp3");
        notDownloadedItem.setMedia(notDownloadedMedia);

        FeedItemFilter showDownloaded = new FeedItemFilter(FeedItemFilter.DOWNLOADED);
        assertTrue(showDownloaded.matches(downloadedItem));
        assertFalse(showDownloaded.matches(notDownloadedItem));

        FeedItemFilter showNotDownloaded = new FeedItemFilter(FeedItemFilter.NOT_DOWNLOADED);
        assertTrue(showNotDownloaded.matches(notDownloadedItem));
        assertFalse(showNotDownloaded.matches(downloadedItem));
    }

    @Test
    public void testMatchesShowHasMediaAndShowNoMedia() {
        FeedItem withMedia = new FeedItem();
        withMedia.setMedia(new FeedMedia(withMedia, "http://example.com/a.mp3", 1, "audio/mp3"));
        FeedItem withoutMedia = new FeedItem();

        FeedItemFilter showHasMedia = new FeedItemFilter(FeedItemFilter.HAS_MEDIA);
        assertTrue(showHasMedia.matches(withMedia));
        assertFalse(showHasMedia.matches(withoutMedia));

        FeedItemFilter showNoMedia = new FeedItemFilter(FeedItemFilter.NO_MEDIA);
        assertTrue(showNoMedia.matches(withoutMedia));
        assertFalse(showNoMedia.matches(withMedia));
    }

    @Test
    public void testMatchesShowIsFavoriteAndShowNotFavorite() {
        FeedItem favoriteItem = new FeedItem();
        favoriteItem.addTag(FeedItem.TAG_FAVORITE);
        FeedItem notFavoriteItem = new FeedItem();

        FeedItemFilter showIsFavorite = new FeedItemFilter(FeedItemFilter.IS_FAVORITE);
        assertTrue(showIsFavorite.matches(favoriteItem));
        assertFalse(showIsFavorite.matches(notFavoriteItem));

        FeedItemFilter showNotFavorite = new FeedItemFilter(FeedItemFilter.NOT_FAVORITE);
        assertTrue(showNotFavorite.matches(notFavoriteItem));
        assertFalse(showNotFavorite.matches(favoriteItem));
    }

    @Test
    public void testMatchesShowInHistory() {
        FeedItem inHistoryItem = new FeedItem();
        FeedMedia inHistoryMedia = new FeedMedia(inHistoryItem, "http://example.com/a.mp3", 1, "audio/mp3");
        inHistoryMedia.setLastPlayedTimeHistory(new Date(12345L));
        inHistoryItem.setMedia(inHistoryMedia);

        FeedItem notInHistoryItem = new FeedItem();
        FeedMedia notInHistoryMedia = new FeedMedia(notInHistoryItem, "http://example.com/b.mp3", 1, "audio/mp3");
        notInHistoryMedia.setLastPlayedTimeHistory(new Date(0L));
        notInHistoryItem.setMedia(notInHistoryMedia);

        FeedItemFilter filter = new FeedItemFilter(FeedItemFilter.IS_IN_HISTORY);

        assertTrue(filter.matches(inHistoryItem));
        assertFalse(filter.matches(notInHistoryItem));
    }

    // Regression guard for the disclosed lastPlayedTimeHistory!! shape (milestone 6): pins that
    // a showInHistory filter still throws NullPointerException when media.lastPlayedTimeHistory
    // is null, from inside FeedItemFilter.matches() itself - not silently swallowed by a future
    // `?.` rewrite, which would let the item pass the filter instead of throwing. Mirrors
    // EmbeddedChapterImageTest.getModelForNullChaptersThrowsNpe for the analogous chapters!!
    // case. Confirmed unreachable in current production: PlaybackHistoryFragment.getFilter()
    // (app/src/main/java/de/danoeh/antennapod/ui/screen/PlaybackHistoryFragment.java) returns
    // FeedItemFilter.unfiltered(), not FILTER_HISTORY; FILTER_HISTORY is only ever used for the
    // SQL-level DBReader path (FeedItemFilterQuery.generateFrom), which excludes null-timestamp
    // rows at the query level and never reaches matches(); IS_IN_HISTORY has zero other
    // production constructors repo-wide.
    @Test
    public void testMatchesShowInHistoryWithNullLastPlayedTimeHistoryThrowsNpe() {
        FeedItem item = new FeedItem();
        FeedMedia media = new FeedMedia(item, "http://example.com/a.mp3", 1, "audio/mp3");
        item.setMedia(media);

        FeedItemFilter filter = new FeedItemFilter(FeedItemFilter.IS_IN_HISTORY);

        NullPointerException exception = assertThrows(NullPointerException.class, () -> filter.matches(item));

        assertNull(exception.getMessage());
    }

    @Test
    public void testMatchesFeedStateBranches() {
        Feed subscribedFeed = FeedMother.anyFeed();
        subscribedFeed.setState(Feed.STATE_SUBSCRIBED);
        Feed archivedFeed = FeedMother.anyFeed();
        archivedFeed.setState(Feed.STATE_ARCHIVED);
        Feed notSubscribedFeed = FeedMother.anyFeed();
        notSubscribedFeed.setState(Feed.STATE_NOT_SUBSCRIBED);

        FeedItem subscribedItem = new FeedItem();
        subscribedItem.setFeed(subscribedFeed);
        FeedItem archivedItem = new FeedItem();
        archivedItem.setFeed(archivedFeed);
        FeedItem notSubscribedItem = new FeedItem();
        notSubscribedItem.setFeed(notSubscribedFeed);

        // Default (no include* flags set): only STATE_SUBSCRIBED items match.
        FeedItemFilter defaultFilter = FeedItemFilter.unfiltered();
        assertTrue(defaultFilter.matches(subscribedItem));
        assertFalse(defaultFilter.matches(archivedItem));
        assertFalse(defaultFilter.matches(notSubscribedItem));

        // Once any include* flag is set, each feed state requires its own explicit flag.
        FeedItemFilter includeArchivedOnly = new FeedItemFilter(FeedItemFilter.INCLUDE_ARCHIVED);
        assertFalse(includeArchivedOnly.matches(subscribedItem));
        assertTrue(includeArchivedOnly.matches(archivedItem));
        assertFalse(includeArchivedOnly.matches(notSubscribedItem));

        FeedItemFilter includeAll = new FeedItemFilter(FeedItemFilter.INCLUDE_ALL_FEED_STATES);
        assertTrue(includeAll.matches(subscribedItem));
        assertTrue(includeAll.matches(archivedItem));
        assertTrue(includeAll.matches(notSubscribedItem));
    }

    @Test
    public void testReferenceEqualityPin() {
        FeedItemFilter filter1 = new FeedItemFilter("played");
        FeedItemFilter filter2 = new FeedItemFilter("played");

        // No equals()/hashCode() defined: two same-content instances must NOT be equal.
        assertNotSame(filter1, filter2);
        assertFalse(filter1.equals(filter2));
    }
}
