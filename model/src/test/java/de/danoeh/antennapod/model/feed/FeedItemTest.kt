package de.danoeh.antennapod.model.feed

import de.danoeh.antennapod.model.feed.FeedItemMother.anyFeedItemWithImage
import de.danoeh.antennapod.model.feed.FeedMother.anyFeed
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class FeedItemTest {

    private lateinit var original: FeedItem
    private lateinit var changedFeedItem: FeedItem

    @Before
    fun setUp() {
        original = anyFeedItemWithImage()
        changedFeedItem = anyFeedItemWithImage()
    }

    @Test
    fun testUpdateFromOther_feedItemImageDownloadUrlChanged() {
        setNewFeedItemImageDownloadUrl()
        original.updateFromOther(changedFeedItem)
        assertFeedItemImageWasUpdated()
    }

    @Test
    fun testUpdateFromOther_feedItemImageRemoved() {
        feedItemImageRemoved()
        original.updateFromOther(changedFeedItem)
        assertFeedItemImageWasNotUpdated()
    }

    @Test
    fun testUpdateFromOther_feedItemImageAdded() {
        original.imageUrl = null
        setNewFeedItemImageDownloadUrl()
        original.updateFromOther(changedFeedItem)
        assertFeedItemImageWasUpdated()
    }

    @Test
    fun testUpdateFromOther_dateChanged() {
        // SimpleDateFormat.parse's Kotlin signature is nullable, but it only returns null when the
        // parse itself fails (it throws ParseException instead in every other case); these literals
        // are always parseable.
        val originalDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("1952-03-11 00:00:00")!!
        val changedDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("1952-03-11 00:42:42")!!
        original.setPubDate(originalDate)
        changedFeedItem.setPubDate(changedDate)
        original.updateFromOther(changedFeedItem)
        // getPubDate() is nullable; pubDate was set two lines above, matching Java's implicit trust.
        assertEquals(changedDate.time, original.getPubDate()!!.time)
    }

    /**
     * Test that a played item loses that state after being marked as new.
     */
    @Test
    fun testMarkPlayedItemAsNew_itemNotPlayed() {
        original.isPlayed = true
        original.setNew()

        assertFalse(original.isPlayed)
    }

    /**
     * Test that a new item loses that state after being marked as played.
     */
    @Test
    fun testMarkNewItemAsPlayed_itemNotNew() {
        original.setNew()
        original.isPlayed = true

        assertFalse(original.isNew)
    }

    /**
     * Test that a new item loses that state after being marked as not played.
     */
    @Test
    fun testMarkNewItemAsNotPlayed_itemNotNew() {
        original.setNew()
        original.isPlayed = false

        assertFalse(original.isNew)
    }

    private fun setNewFeedItemImageDownloadUrl() {
        changedFeedItem.imageUrl = "http://example.com/new_picture"
    }

    private fun feedItemImageRemoved() {
        changedFeedItem.imageUrl = null
    }

    private fun assertFeedItemImageWasUpdated() {
        assertEquals(original.imageUrl, changedFeedItem.imageUrl)
    }

    private fun assertFeedItemImageWasNotUpdated() {
        assertEquals(anyFeedItemWithImage().imageUrl, original.imageUrl)
    }

    /**
     * If one of `description` or `content:encoded` is null, use the other one.
     */
    @Test
    fun testShownotesNullValues() {
        testShownotes(null, TEXT_LONG)
        testShownotes(TEXT_LONG, null)
    }

    /**
     * If `description` is reasonably longer than `content:encoded`, use `description`.
     */
    @Test
    fun testShownotesLength() {
        testShownotes(TEXT_SHORT, TEXT_LONG)
        testShownotes(TEXT_LONG, TEXT_SHORT)
    }

    /**
     * Checks if the shownotes equal TEXT_LONG, using the given `description` and `content:encoded`.
     *
     * @param description Description of the feed item
     * @param contentEncoded `content:encoded` of the feed item
     */
    private fun testShownotes(description: String?, contentEncoded: String?) {
        val item = FeedItem()
        item.setDescriptionIfLonger(description)
        item.setDescriptionIfLonger(contentEncoded)
        assertEquals(TEXT_LONG, item.description)
    }

    @Test
    fun equalsSameIdIsEqual() {
        val a = anyFeedItemWithImage()
        val b = anyFeedItemWithImage()
        a.id = 1
        b.id = 1
        assertEquals(a, b)
    }

    @Test
    fun equalsDifferentIdNotEqual() {
        val a = anyFeedItemWithImage()
        val b = anyFeedItemWithImage()
        a.id = 1
        b.id = 2
        assertNotEquals(a, b)
    }

    @Test
    fun hashCodeMatchesForSameId() {
        val a = anyFeedItemWithImage()
        val b = anyFeedItemWithImage()
        a.id = 1
        b.id = 1
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun getIdentifyingValuePrefersItemIdentifier() {
        val item = createItem("item-identifier", "title", "http://example.com/link")
        assertEquals("item-identifier", item.getIdentifyingValue())
    }

    @Test
    fun getIdentifyingValueFallsBackToTitle() {
        val item = createItem(null, "title", "http://example.com/link")
        assertEquals("title", item.getIdentifyingValue())
    }

    @Test
    fun getIdentifyingValueFallsBackToLink() {
        val item = createItem(null, null, "http://example.com/link")
        assertEquals("http://example.com/link", item.getIdentifyingValue())
    }

    @Test
    fun getPubDateReturnsDefensiveCopy() {
        val item = FeedItem()
        assertNull(item.getPubDate())

        val original = Date(123456789L)
        item.setPubDate(original)
        val returned = item.getPubDate()
        returned!!.time = 0L

        // getPubDate() is nullable; pubDate was set two lines above, matching Java's implicit trust.
        assertEquals(123456789L, item.getPubDate()!!.time)
    }

    @Test
    fun setPubDateStoresDefensiveCopy() {
        val item = FeedItem()
        val source = Date(123456789L)
        item.setPubDate(source)
        source.time = 0L

        // getPubDate() is nullable; pubDate was set two lines above, matching Java's implicit trust.
        assertEquals(123456789L, item.getPubDate()!!.time)
    }

    @Test
    fun serializationDropsTransientFeedAndChapters() {
        // media/transcript are intentionally left null: both are non-transient and media is typed
        // FeedMedia (Tier C, implements Playable -> Parcelable), so populating it could drag an
        // accidental Tier C dependency (and a possible NotSerializableException) into this bare-JVM
        // Tier A test.
        val item = FeedItem()
        item.id = 42
        item.title = "Serialize me"
        item.feed = anyFeed()
        item.chapters = Collections.singletonList(Chapter(0, "Chapter 1", null, null))
        val pubDate = Date(987654321L)
        item.setPubDate(pubDate)

        val byteStream = ByteArrayOutputStream()
        ObjectOutputStream(byteStream).use { out -> out.writeObject(item) }
        val deserialized = ObjectInputStream(ByteArrayInputStream(byteStream.toByteArray())).use { input ->
            input.readObject() as FeedItem
        }

        assertEquals(42L, deserialized.id)
        assertEquals("Serialize me", deserialized.title)
        assertNull(deserialized.feed)
        assertNull(deserialized.chapters)
        // pubDate's backing field was renamed pubDate -> pubDateField during the Kotlin conversion
        // (see the disclosure comment above FeedItem.kt's pubDateField); this assertion pins that the
        // non-transient field still survives an intra-session ObjectOutputStream/ObjectInputStream
        // round trip under its new name.
        assertEquals(pubDate, deserialized.getPubDate())
    }

    private fun createItem(itemIdentifier: String?, title: String?, link: String?): FeedItem {
        return FeedItem(1, title, itemIdentifier, link, null, FeedItem.UNPLAYED, null)
    }

    companion object {
        private const val TEXT_LONG = "Lorem ipsum dolor sit amet, consectetur adipiscing elit."
        private const val TEXT_SHORT = "Lorem ipsum"
    }
}
