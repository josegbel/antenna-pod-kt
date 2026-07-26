package de.danoeh.antennapod.event;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.danoeh.antennapod.model.download.DownloadStatus;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class EpisodeDownloadEventTest {

    private FeedItem itemWithDownloadUrl(String downloadUrl) {
        FeedItem item = new FeedItem();
        FeedMedia media = new FeedMedia(item, downloadUrl, 0L, "audio/mp3");
        item.setMedia(media);
        return item;
    }

    @Test
    public void getUrlsReturnsLiveViewOfPassedMap() {
        Map<String, DownloadStatus> map = new HashMap<>();
        map.put("url1", new DownloadStatus(0, 0));
        EpisodeDownloadEvent event = new EpisodeDownloadEvent(map);

        Set<String> urls = event.getUrls();
        assertEquals(1, urls.size());
        assertTrue(urls.contains("url1"));

        map.put("url2", new DownloadStatus(0, 0));
        assertEquals(2, event.getUrls().size());
        assertTrue(event.getUrls().contains("url2"));
    }

    @Test
    public void indexOfItemWithDownloadUrlReturnsMatchingIndex() {
        List<FeedItem> items = Arrays.asList(itemWithDownloadUrl("a"), itemWithDownloadUrl("b"));
        assertEquals(1, EpisodeDownloadEvent.indexOfItemWithDownloadUrl(items, "b"));
    }

    @Test
    public void indexOfItemWithDownloadUrlReturnsMinusOneWhenMissing() {
        List<FeedItem> items = Arrays.asList(itemWithDownloadUrl("a"), itemWithDownloadUrl("b"));
        assertEquals(-1, EpisodeDownloadEvent.indexOfItemWithDownloadUrl(items, "missing"));
    }

    @Test
    public void indexOfItemWithDownloadUrlSkipsNullItems() {
        List<FeedItem> items = Arrays.asList(null, itemWithDownloadUrl("b"));
        assertEquals(1, EpisodeDownloadEvent.indexOfItemWithDownloadUrl(items, "b"));
    }

    @Test
    public void indexOfItemWithDownloadUrlSkipsItemsWithNullMedia() {
        FeedItem itemWithoutMedia = new FeedItem();
        List<FeedItem> items = Arrays.asList(itemWithoutMedia, itemWithDownloadUrl("b"));
        assertEquals(1, EpisodeDownloadEvent.indexOfItemWithDownloadUrl(items, "b"));
    }

    @Test
    public void indexOfItemWithDownloadUrlThrowsNpeWhenMediaHasNullDownloadUrl() {
        FeedItem item = itemWithDownloadUrl(null);
        List<FeedItem> items = Arrays.asList(item);
        assertThrows(NullPointerException.class,
                () -> EpisodeDownloadEvent.indexOfItemWithDownloadUrl(items, "b"));
    }
}
