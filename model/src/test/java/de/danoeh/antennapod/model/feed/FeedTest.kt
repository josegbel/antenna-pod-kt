package de.danoeh.antennapod.model.feed

import de.danoeh.antennapod.model.feed.FeedMother.IMAGE_URL
import de.danoeh.antennapod.model.feed.FeedMother.anyFeed
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class FeedTest {

    private lateinit var original: Feed
    private lateinit var changedFeed: Feed

    @Before
    fun setUp() {
        original = anyFeed()
        changedFeed = anyFeed()
    }

    @Test
    fun testUpdateFromOther_feedImageDownloadUrlChanged() {
        changedFeed.imageUrl = "http://example.com/new_picture"
        original.updateFromOther(changedFeed)
        assertEquals(original.imageUrl, changedFeed.imageUrl)
    }

    @Test
    fun testUpdateFromOther_feedImageRemoved() {
        changedFeed.imageUrl = null
        original.updateFromOther(changedFeed)
        assertEquals(anyFeed().imageUrl, original.imageUrl)
    }

    @Test
    fun testUpdateFromOther_feedImageAdded() {
        original.imageUrl = null
        changedFeed.imageUrl = "http://example.com/new_picture"
        original.updateFromOther(changedFeed)
        assertEquals(original.imageUrl, changedFeed.imageUrl)
    }

    @Test
    fun testSetSortOrder_OnlyIntraFeedSortAllowed() {
        for (sortOrder in SortOrder.entries) {
            if (sortOrder.scope == SortOrder.Scope.INTRA_FEED) {
                original.sortOrder = sortOrder // should be okay
            } else {
                assertThrows(IllegalArgumentException::class.java) { original.sortOrder = sortOrder }
            }
        }
    }

    @Test
    fun testSetSortOrder_NullAllowed() {
        original.sortOrder = null // should be okay
    }

    @Test
    fun equalsSameIdIsEqual() {
        val a = anyFeed()
        val b = anyFeed()
        a.id = 1
        b.id = 1
        assertEquals(a, b)
    }

    @Test
    fun equalsDifferentIdNotEqual() {
        val a = anyFeed()
        val b = anyFeed()
        a.id = 1
        b.id = 2
        assertNotEquals(a, b)
    }

    @Test
    fun equalsDifferentClassNotEqual() {
        val a = anyFeed()
        assertNotEquals(a, "not a feed")
    }

    @Test
    fun hashCodeMatchesForSameId() {
        val a = anyFeed()
        val b = anyFeed()
        a.id = 1
        b.id = 1
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun getIdentifyingValuePrefersFeedIdentifier() {
        val feed = createFeed(
            "feed-identifier",
            "http://example.com/download",
            "title",
            null,
            "http://example.com/link"
        )
        assertEquals("feed-identifier", feed.getIdentifyingValue())
    }

    @Test
    fun getIdentifyingValueFallsBackToDownloadUrl() {
        val feed = createFeed(null, "http://example.com/download", "title", null, "http://example.com/link")
        assertEquals("http://example.com/download", feed.getIdentifyingValue())
    }

    @Test
    fun getIdentifyingValueFallsBackToTitle() {
        val feed = createFeed(null, null, "title", null, "http://example.com/link")
        assertEquals("title", feed.getIdentifyingValue())
    }

    @Test
    fun getIdentifyingValueFallsBackToLink() {
        val feed = createFeed(null, null, null, null, "http://example.com/link")
        assertEquals("http://example.com/link", feed.getIdentifyingValue())
    }

    @Test
    fun getHumanReadableIdentifierPrefersCustomTitle() {
        val feed = createFeed(
            null,
            "http://example.com/download",
            "title",
            "custom title",
            "http://example.com/link"
        )
        assertEquals("custom title", feed.getHumanReadableIdentifier())
    }

    @Test
    fun getHumanReadableIdentifierFallsBackToFeedTitle() {
        val feed = createFeed(null, "http://example.com/download", "title", null, "http://example.com/link")
        assertEquals("title", feed.getHumanReadableIdentifier())
    }

    @Test
    fun getHumanReadableIdentifierFallsBackToDownloadUrl() {
        val feed = createFeed(null, "http://example.com/download", null, null, "http://example.com/link")
        assertEquals("http://example.com/download", feed.getHumanReadableIdentifier())
    }

    @Test
    fun constructorParsesFundingLinks() {
        val feed = Feed(
            1, null, "title", "http://example.com/link", "description",
            "http://example.com/pay", "author", "en", null, null, IMAGE_URL, null,
            "http://example.com/feed", 0
        )
        // getPaymentLinks() delegates to FeedFunding.extractPaymentLinks, which only returns null
        // for a blank input; "http://example.com/pay" above is non-blank, so this is always non-null.
        val paymentLinks = feed.getPaymentLinks()!!
        assertEquals(1, paymentLinks.size)
        assertEquals("http://example.com/pay", paymentLinks.get(0).url)
    }

    @Test
    fun serializationRoundTripPreservesIdTitleAndFunding() {
        val feed = anyFeed()
        feed.id = 99
        feed.setCustomTitle("My Custom Title")

        val byteStream = ByteArrayOutputStream()
        ObjectOutputStream(byteStream).use { out -> out.writeObject(feed) }
        val deserialized = ObjectInputStream(ByteArrayInputStream(byteStream.toByteArray())).use { input ->
            input.readObject() as Feed
        }

        assertEquals(99L, deserialized.id)
        assertEquals(feed.feedTitle, deserialized.feedTitle)
        assertEquals("My Custom Title", deserialized.getCustomTitle())
        assertEquals(feed.getPaymentLinks(), deserialized.getPaymentLinks())
    }

    private fun createFeed(
        feedIdentifier: String?,
        downloadUrl: String?,
        title: String?,
        customTitle: String?,
        link: String?
    ): Feed {
        return Feed(
            1, null, title, customTitle, link, null, null, null, null, null,
            feedIdentifier, null, null, downloadUrl, 0, false, null, null, null, false, Feed.STATE_SUBSCRIBED
        )
    }
}
