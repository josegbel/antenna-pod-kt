package de.danoeh.antennapod.model.feed

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FeedFilterTest {

    @Test
    fun testNullFilter() {
        val filter = FeedFilter()
        val item = FeedItem()
        item.title = "Hello world"

        assertFalse(filter.excludeOnly())
        assertFalse(filter.includeOnly())
        assertEquals("", filter.getExcludeFilterRaw())
        assertEquals("", filter.getIncludeFilterRaw())
        assertTrue(filter.shouldAutoDownload(item))
    }

    @Test
    fun testBasicIncludeFilter() {
        val includeFilter = "Hello"
        val filter = FeedFilter(includeFilter, "")
        val item = FeedItem()
        item.title = "Hello world"

        val item2 = FeedItem()
        item2.title = "Don't include me"

        assertFalse(filter.excludeOnly())
        assertTrue(filter.includeOnly())
        assertEquals("", filter.getExcludeFilterRaw())
        assertEquals(includeFilter, filter.getIncludeFilterRaw())
        assertTrue(filter.shouldAutoDownload(item))
        assertFalse(filter.shouldAutoDownload(item2))
    }

    @Test
    fun testBasicExcludeFilter() {
        val excludeFilter = "Hello"
        val filter = FeedFilter("", excludeFilter)
        val item = FeedItem()
        item.title = "Hello world"

        val item2 = FeedItem()
        item2.title = "Item2"

        assertTrue(filter.excludeOnly())
        assertFalse(filter.includeOnly())
        assertEquals(excludeFilter, filter.getExcludeFilterRaw())
        assertEquals("", filter.getIncludeFilterRaw())
        assertFalse(filter.shouldAutoDownload(item))
        assertTrue(filter.shouldAutoDownload(item2))
    }

    @Test
    fun testComplexIncludeFilter() {
        val includeFilter = "Hello \n\"Two words\""
        val filter = FeedFilter(includeFilter, "")
        val item = FeedItem()
        item.title = "hello world"

        val item2 = FeedItem()
        item2.title = "Two three words"

        val item3 = FeedItem()
        item3.title = "One two words"

        assertFalse(filter.excludeOnly())
        assertTrue(filter.includeOnly())
        assertEquals("", filter.getExcludeFilterRaw())
        assertEquals(includeFilter, filter.getIncludeFilterRaw())
        assertTrue(filter.shouldAutoDownload(item))
        assertFalse(filter.shouldAutoDownload(item2))
        assertTrue(filter.shouldAutoDownload(item3))
    }

    @Test
    fun testComplexExcludeFilter() {
        val excludeFilter = "Hello \"Two words\""
        val filter = FeedFilter("", excludeFilter)
        val item = FeedItem()
        item.title = "hello world"

        val item2 = FeedItem()
        item2.title = "One three words"

        val item3 = FeedItem()
        item3.title = "One two words"

        assertTrue(filter.excludeOnly())
        assertFalse(filter.includeOnly())
        assertEquals(excludeFilter, filter.getExcludeFilterRaw())
        assertEquals("", filter.getIncludeFilterRaw())
        assertFalse(filter.shouldAutoDownload(item))
        assertTrue(filter.shouldAutoDownload(item2))
        assertFalse(filter.shouldAutoDownload(item3))
    }

    @Test
    fun testComboFilter() {
        val includeFilter = "Hello world"
        val excludeFilter = "dislike"
        val filter = FeedFilter(includeFilter, excludeFilter)

        val download = FeedItem()
        download.title = "Hello everyone!"
        // because, while it has words from the include filter it also has exclude words
        val doNotDownload = FeedItem()
        doNotDownload.title = "I dislike the world"
        // because it has no words from the include filter
        val doNotDownload2 = FeedItem()
        doNotDownload2.title = "no words to include"

        assertTrue(filter.hasExcludeFilter())
        assertTrue(filter.hasIncludeFilter())
        assertTrue(filter.shouldAutoDownload(download))
        assertFalse(filter.shouldAutoDownload(doNotDownload))
        assertFalse(filter.shouldAutoDownload(doNotDownload2))
    }

    @Test
    fun testMinimalDurationFilter() {
        val download = FeedItem()
        download.title = "Hello friend!"
        val downloadMedia = FeedMediaMother.anyFeedMedia()
        downloadMedia.duration = TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES).toInt()
        download.media = downloadMedia
        // because duration of the media in unknown
        val download2 = FeedItem()
        download2.title = "Hello friend!"
        val unknownDurationMedia = FeedMediaMother.anyFeedMedia()
        download2.media = unknownDurationMedia
        // because it is not long enough
        val doNotDownload = FeedItem()
        doNotDownload.title = "Hello friend!"
        val doNotDownloadMedia = FeedMediaMother.anyFeedMedia()
        doNotDownloadMedia.duration = TimeUnit.MILLISECONDS.convert(2, TimeUnit.MINUTES).toInt()
        doNotDownload.media = doNotDownloadMedia

        val minimalDurationFilter = 3 * 60
        val filter = FeedFilter("", "", minimalDurationFilter)

        assertTrue(filter.hasMinimalDurationFilter())
        assertTrue(filter.shouldAutoDownload(download))
        assertFalse(filter.shouldAutoDownload(doNotDownload))
        assertTrue(filter.shouldAutoDownload(download2))
    }

    @Test
    fun testReferenceEqualityPin() {
        val filter1 = FeedFilter("Hello", "", -1)
        val filter2 = FeedFilter("Hello", "", -1)

        // No equals()/hashCode() defined: two same-content instances must NOT be equal.
        assertNotSame(filter1, filter2)
        assertFalse(filter1.equals(filter2))
    }

    @Test
    fun testNullIncludeFilterFieldEdgeCase() {
        val filter = FeedFilter(null, "", -1)

        // getIncludeFilter() null-checks defensively and returns an empty list.
        assertTrue(filter.getIncludeFilter().isEmpty())
        // hasIncludeFilter() has no null-check and throws on a null includeFilter,
        // an existing inconsistency that must be preserved exactly, not "fixed".
        try {
            filter.hasIncludeFilter()
            fail("Expected a NullPointerException")
        } catch (expected: NullPointerException) {
            // expected
        }
    }

    @Test
    fun testShouldAutoDownloadThrowsOnNullIncludeFilter() {
        val filter = FeedFilter(null, "", -1)
        val item = FeedItem()
        item.title = "Hello world"

        // shouldAutoDownload() has no null-check on includeFilter, matching the
        // original unguarded Java's NullPointerException at the same call site.
        try {
            filter.shouldAutoDownload(item)
            fail("Expected a NullPointerException")
        } catch (expected: NullPointerException) {
            // expected
        }
    }

    @Test
    fun testShouldAutoDownloadThrowsOnNullExcludeFilter() {
        val filter = FeedFilter("", null, -1)
        val item = FeedItem()
        item.title = "Hello world"

        // shouldAutoDownload() has no null-check on excludeFilter, matching the
        // original unguarded Java's NullPointerException at the same call site.
        try {
            filter.shouldAutoDownload(item)
            fail("Expected a NullPointerException")
        } catch (expected: NullPointerException) {
            // expected
        }
    }
}
