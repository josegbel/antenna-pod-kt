package de.danoeh.antennapod.event

import de.danoeh.antennapod.model.download.DownloadStatus
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import java.util.Arrays
import java.util.HashMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeDownloadEventTest {

    private fun itemWithDownloadUrl(downloadUrl: String?): FeedItem {
        val item = FeedItem()
        val media = FeedMedia(item, downloadUrl, 0L, "audio/mp3")
        item.media = media
        return item
    }

    @Test
    fun getUrlsReturnsLiveViewOfPassedMap() {
        val map: MutableMap<String, DownloadStatus> = HashMap()
        map.put("url1", DownloadStatus(0, 0))
        val event = EpisodeDownloadEvent(map)

        val urls = event.getUrls()
        assertEquals(1, urls.size)
        assertTrue(urls.contains("url1"))

        map.put("url2", DownloadStatus(0, 0))
        assertEquals(2, event.getUrls().size)
        assertTrue(event.getUrls().contains("url2"))
    }

    @Test
    fun indexOfItemWithDownloadUrlReturnsMatchingIndex() {
        val items = Arrays.asList(itemWithDownloadUrl("a"), itemWithDownloadUrl("b"))
        assertEquals(1, EpisodeDownloadEvent.indexOfItemWithDownloadUrl(items, "b"))
    }

    @Test
    fun indexOfItemWithDownloadUrlReturnsMinusOneWhenMissing() {
        val items = Arrays.asList(itemWithDownloadUrl("a"), itemWithDownloadUrl("b"))
        assertEquals(-1, EpisodeDownloadEvent.indexOfItemWithDownloadUrl(items, "missing"))
    }

    @Test
    fun indexOfItemWithDownloadUrlSkipsNullItems() {
        val items = Arrays.asList(null, itemWithDownloadUrl("b"))
        assertEquals(1, EpisodeDownloadEvent.indexOfItemWithDownloadUrl(items, "b"))
    }

    @Test
    fun indexOfItemWithDownloadUrlSkipsItemsWithNullMedia() {
        val itemWithoutMedia = FeedItem()
        val items = Arrays.asList(itemWithoutMedia, itemWithDownloadUrl("b"))
        assertEquals(1, EpisodeDownloadEvent.indexOfItemWithDownloadUrl(items, "b"))
    }

    @Test
    fun indexOfItemWithDownloadUrlThrowsNpeWhenMediaHasNullDownloadUrl() {
        val item = itemWithDownloadUrl(null)
        val items = Arrays.asList(item)
        assertThrows(NullPointerException::class.java) { EpisodeDownloadEvent.indexOfItemWithDownloadUrl(items, "b") }
    }
}
