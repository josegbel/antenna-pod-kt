package de.danoeh.antennapod.model.feed

import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedItemFilterTest {

    @Test
    fun testConstructorFromSingleCsvString() {
        val filter = FeedItemFilter("played,queued")

        assertTrue(filter.showPlayed)
        assertTrue(filter.showQueued)
        assertFalse(filter.showUnplayed)
        assertFalse(filter.showDownloaded)
    }

    @Test
    fun testConstructorFromVarargs() {
        val filter = FeedItemFilter("played", "queued")

        assertTrue(filter.showPlayed)
        assertTrue(filter.showQueued)
        assertFalse(filter.showUnplayed)
        assertFalse(filter.showDownloaded)
    }

    @Test
    fun testConstructorFromFilterPlusAdditionalProperties() {
        val base = FeedItemFilter("played")
        val extended = FeedItemFilter(base, "queued")

        assertTrue(extended.showPlayed)
        assertTrue(extended.showQueued)
    }

    @Test
    fun testConstructorFromFilterWithZeroAdditionalPropertiesYieldsNoTrailingEmptyEntry() {
        // Mirrors the real Feed.java call site `new FeedItemFilter(filter)`: the
        // (FeedItemFilter, String...) constructor joins the base filter's values plus a trailing
        // comma even when 0 additionalProperties are passed, and the resulting properties array
        // must not contain a phantom "" entry from that trailing comma (Java's String.split with
        // the default limit of 0 strips trailing empty strings; this must be preserved exactly).
        val base = FeedItemFilter("played")

        val copy = FeedItemFilter(base)

        assertEquals(1, copy.getValues().size)
        assertEquals("played", copy.getValues()[0])
        assertTrue(copy.showPlayed)
    }

    @Test
    fun testUnfiltered() {
        val filter = FeedItemFilter.unfiltered()

        assertEquals(0, filter.getValues().size)
        assertFalse(filter.showPlayed)
        assertFalse(filter.showUnplayed)
        assertFalse(filter.includeSubscribed)
    }

    @Test
    fun testEmptyStringYieldsZeroLengthProperties() {
        val filter = FeedItemFilter("")

        assertEquals(0, filter.getValues().size)
    }

    @Test
    fun testWithoutRemovesExactlyThatPropertyAndNoOther() {
        val filter = FeedItemFilter("played,queued,downloaded")

        val result = filter.without(FeedItemFilter.QUEUED)

        val values = result.getValuesList()
        assertEquals(2, values.size)
        assertTrue(values.contains(FeedItemFilter.PLAYED))
        assertTrue(values.contains(FeedItemFilter.DOWNLOADED))
        assertFalse(values.contains(FeedItemFilter.QUEUED))
    }

    @Test
    fun testGetValuesReturnsDefensiveClone() {
        val filter = FeedItemFilter("played")

        val values = filter.getValues()
        values[0] = "hacked"

        assertEquals("played", filter.getValues()[0])
    }

    @Test
    fun testGetValuesListDoesNotDefensivelyCopy() {
        // Pinning existing behavior: getValuesList() wraps the backing array directly
        // (Arrays.asList), unlike getValues() which clones. A write through the list
        // mutates state visible to subsequent calls - this is not a bug to fix here.
        val filter = FeedItemFilter("played")

        val values = filter.getValuesList()
        // getValuesList()'s declared Kotlin return type is read-only List<String>, but the
        // underlying instance is really an Arrays.asList()-backed mutable list (see the comment
        // above) - this cast exercises exactly that documented, intentional non-defensive behavior.
        (values as MutableList<String>)[0] = "unplayed"

        assertEquals("unplayed", filter.getValuesList().get(0))
        assertEquals("unplayed", filter.getValues()[0])
    }

    @Test
    fun testMatchesShowPlayedAndShowUnplayed() {
        val playedItem = FeedItem()
        playedItem.setPlayState(FeedItem.PLAYED)
        val unplayedItem = FeedItem()
        unplayedItem.setPlayState(FeedItem.UNPLAYED)

        val showPlayed = FeedItemFilter(FeedItemFilter.PLAYED)
        assertTrue(showPlayed.matches(playedItem))
        assertFalse(showPlayed.matches(unplayedItem))

        val showUnplayed = FeedItemFilter(FeedItemFilter.UNPLAYED)
        assertTrue(showUnplayed.matches(unplayedItem))
        assertFalse(showUnplayed.matches(playedItem))
    }

    @Test
    fun testMatchesShowNew() {
        val newItem = FeedItem()
        newItem.setPlayState(FeedItem.NEW)
        val oldItem = FeedItem()
        oldItem.setPlayState(FeedItem.UNPLAYED)

        val filter = FeedItemFilter(FeedItemFilter.NEW)

        assertTrue(filter.matches(newItem))
        assertFalse(filter.matches(oldItem))
    }

    @Test
    fun testMatchesShowPausedAndShowNotPaused() {
        val inProgressItem = FeedItem()
        val inProgressMedia = FeedMedia(inProgressItem, "http://example.com/a.mp3", 1, "audio/mp3")
        inProgressMedia.position = 1000
        inProgressItem.media = inProgressMedia

        val freshItem = FeedItem()
        val freshMedia = FeedMedia(freshItem, "http://example.com/b.mp3", 1, "audio/mp3")
        freshItem.media = freshMedia

        val showPaused = FeedItemFilter(FeedItemFilter.PAUSED)
        assertTrue(showPaused.matches(inProgressItem))
        assertFalse(showPaused.matches(freshItem))

        val showNotPaused = FeedItemFilter(FeedItemFilter.NOT_PAUSED)
        assertTrue(showNotPaused.matches(freshItem))
        assertFalse(showNotPaused.matches(inProgressItem))
    }

    @Test
    fun testMatchesShowQueuedAndShowNotQueued() {
        val queuedItem = FeedItem()
        queuedItem.addTag(FeedItem.TAG_QUEUE)
        val notQueuedItem = FeedItem()

        val showQueued = FeedItemFilter(FeedItemFilter.QUEUED)
        assertTrue(showQueued.matches(queuedItem))
        assertFalse(showQueued.matches(notQueuedItem))

        val showNotQueued = FeedItemFilter(FeedItemFilter.NOT_QUEUED)
        assertTrue(showNotQueued.matches(notQueuedItem))
        assertFalse(showNotQueued.matches(queuedItem))
    }

    @Test
    fun testMatchesShowDownloadedAndShowNotDownloaded() {
        val downloadedItem = FeedItem()
        val downloadedMedia = FeedMedia(downloadedItem, "http://example.com/a.mp3", 1, "audio/mp3")
        downloadedMedia.setDownloaded(true, System.currentTimeMillis())
        downloadedItem.media = downloadedMedia

        val notDownloadedItem = FeedItem()
        val notDownloadedMedia = FeedMedia(notDownloadedItem, "http://example.com/b.mp3", 1, "audio/mp3")
        notDownloadedItem.media = notDownloadedMedia

        val showDownloaded = FeedItemFilter(FeedItemFilter.DOWNLOADED)
        assertTrue(showDownloaded.matches(downloadedItem))
        assertFalse(showDownloaded.matches(notDownloadedItem))

        val showNotDownloaded = FeedItemFilter(FeedItemFilter.NOT_DOWNLOADED)
        assertTrue(showNotDownloaded.matches(notDownloadedItem))
        assertFalse(showNotDownloaded.matches(downloadedItem))
    }

    @Test
    fun testMatchesShowHasMediaAndShowNoMedia() {
        val withMedia = FeedItem()
        withMedia.media = FeedMedia(withMedia, "http://example.com/a.mp3", 1, "audio/mp3")
        val withoutMedia = FeedItem()

        val showHasMedia = FeedItemFilter(FeedItemFilter.HAS_MEDIA)
        assertTrue(showHasMedia.matches(withMedia))
        assertFalse(showHasMedia.matches(withoutMedia))

        val showNoMedia = FeedItemFilter(FeedItemFilter.NO_MEDIA)
        assertTrue(showNoMedia.matches(withoutMedia))
        assertFalse(showNoMedia.matches(withMedia))
    }

    @Test
    fun testMatchesShowIsFavoriteAndShowNotFavorite() {
        val favoriteItem = FeedItem()
        favoriteItem.addTag(FeedItem.TAG_FAVORITE)
        val notFavoriteItem = FeedItem()

        val showIsFavorite = FeedItemFilter(FeedItemFilter.IS_FAVORITE)
        assertTrue(showIsFavorite.matches(favoriteItem))
        assertFalse(showIsFavorite.matches(notFavoriteItem))

        val showNotFavorite = FeedItemFilter(FeedItemFilter.NOT_FAVORITE)
        assertTrue(showNotFavorite.matches(notFavoriteItem))
        assertFalse(showNotFavorite.matches(favoriteItem))
    }

    @Test
    fun testMatchesShowInHistory() {
        val inHistoryItem = FeedItem()
        val inHistoryMedia = FeedMedia(inHistoryItem, "http://example.com/a.mp3", 1, "audio/mp3")
        inHistoryMedia.lastPlayedTimeHistory = Date(12345L)
        inHistoryItem.media = inHistoryMedia

        val notInHistoryItem = FeedItem()
        val notInHistoryMedia = FeedMedia(notInHistoryItem, "http://example.com/b.mp3", 1, "audio/mp3")
        notInHistoryMedia.lastPlayedTimeHistory = Date(0L)
        notInHistoryItem.media = notInHistoryMedia

        val filter = FeedItemFilter(FeedItemFilter.IS_IN_HISTORY)

        assertTrue(filter.matches(inHistoryItem))
        assertFalse(filter.matches(notInHistoryItem))
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
    fun testMatchesShowInHistoryWithNullLastPlayedTimeHistoryThrowsNpe() {
        val item = FeedItem()
        val media = FeedMedia(item, "http://example.com/a.mp3", 1, "audio/mp3")
        item.media = media

        val filter = FeedItemFilter(FeedItemFilter.IS_IN_HISTORY)

        val exception = assertThrows(NullPointerException::class.java) { filter.matches(item) }

        assertNull(exception.message)
    }

    @Test
    fun testMatchesFeedStateBranches() {
        val subscribedFeed = FeedMother.anyFeed()
        subscribedFeed.setState(Feed.STATE_SUBSCRIBED)
        val archivedFeed = FeedMother.anyFeed()
        archivedFeed.setState(Feed.STATE_ARCHIVED)
        val notSubscribedFeed = FeedMother.anyFeed()
        notSubscribedFeed.setState(Feed.STATE_NOT_SUBSCRIBED)

        val subscribedItem = FeedItem()
        subscribedItem.feed = subscribedFeed
        val archivedItem = FeedItem()
        archivedItem.feed = archivedFeed
        val notSubscribedItem = FeedItem()
        notSubscribedItem.feed = notSubscribedFeed

        // Default (no include* flags set): only STATE_SUBSCRIBED items match.
        val defaultFilter = FeedItemFilter.unfiltered()
        assertTrue(defaultFilter.matches(subscribedItem))
        assertFalse(defaultFilter.matches(archivedItem))
        assertFalse(defaultFilter.matches(notSubscribedItem))

        // Once any include* flag is set, each feed state requires its own explicit flag.
        val includeArchivedOnly = FeedItemFilter(FeedItemFilter.INCLUDE_ARCHIVED)
        assertFalse(includeArchivedOnly.matches(subscribedItem))
        assertTrue(includeArchivedOnly.matches(archivedItem))
        assertFalse(includeArchivedOnly.matches(notSubscribedItem))

        val includeAll = FeedItemFilter(FeedItemFilter.INCLUDE_ALL_FEED_STATES)
        assertTrue(includeAll.matches(subscribedItem))
        assertTrue(includeAll.matches(archivedItem))
        assertTrue(includeAll.matches(notSubscribedItem))
    }

    @Test
    fun testReferenceEqualityPin() {
        val filter1 = FeedItemFilter("played")
        val filter2 = FeedItemFilter("played")

        // No equals()/hashCode() defined: two same-content instances must NOT be equal.
        assertNotSame(filter1, filter2)
        assertFalse(filter1.equals(filter2))
    }
}
