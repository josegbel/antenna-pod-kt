package de.danoeh.antennapod.model.feed

import java.util.Collections
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class FeedItemFallbackLinkTest(
    private val msg: String,
    private val feedLink: String?,
    private val itemLink: String?,
    private val expected: String?
) {

    @Test
    fun testLinkWithFallback() {
        val actual = createFeedItem(feedLink, itemLink).getLinkWithFallback()
        assertEquals(msg, expected, actual)
    }

    companion object {
        private const val FEED_LINK = "http://example.com"
        private const val ITEM_LINK = "http://example.com/feedItem1"

        @JvmStatic
        @Parameterized.Parameters
        fun data(): Collection<Array<Any?>> = listOf(
            arrayOf<Any?>("average", FEED_LINK, ITEM_LINK, ITEM_LINK),
            arrayOf<Any?>("null item link - fallback to feed", FEED_LINK, null, FEED_LINK),
            arrayOf<Any?>("empty item link - same as null", FEED_LINK, "", FEED_LINK),
            arrayOf<Any?>("blank item link - same as null", FEED_LINK, "  ", FEED_LINK),
            arrayOf<Any?>("fallback, but feed link is null too", null, null, null),
            arrayOf<Any?>("fallback - but empty feed link - same as null", "", null, null),
            arrayOf<Any?>("fallback - but blank feed link - same as null", "  ", null, null)
        )

        private fun createFeedItem(feedLink: String?, itemLink: String?): FeedItem {
            val feed = Feed("http://example.com/feed", null)
            feed.link = feedLink
            val feedItem = FeedItem()
            feedItem.link = itemLink
            feedItem.feed = feed
            feed.items = Collections.singletonList(feedItem)
            return feedItem
        }
    }
}
