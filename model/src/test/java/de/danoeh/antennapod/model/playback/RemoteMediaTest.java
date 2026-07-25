package de.danoeh.antennapod.model.playback;

import android.os.Parcel;

import de.danoeh.antennapod.model.feed.Chapter;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

// Characterizes RemoteMedia's Parcelable/equals-hashCode behavior against the live Java
// implementation, under Robolectric (this milestone's disclosed one-milestone exception to
// :model's Robolectric-free precedent).
@RunWith(RobolectricTestRunner.class)
public class RemoteMediaTest {

    @Test
    public void parcelRoundTripDropsChaptersAndConvertsNullPubDateToEpoch() {
        RemoteMedia original = new RemoteMedia("http://example.com/ep.mp3", "item-1", "http://example.com/feed",
                "feedTitle", "episodeTitle", "http://example.com/link", "author",
                "http://example.com/image.png", "http://example.com/feedlink", "audio/mp3",
                null, "notes");
        List<Chapter> chapters = new ArrayList<>();
        chapters.add(new Chapter(0, "chapter1", "http://example.com/1", null));
        original.setChapters(chapters);

        RemoteMedia restored = parcelRoundTrip(original);

        assertNull(restored.getChapters());
        assertEquals(new Date(0), restored.getPubDate());
    }

    @Test
    public void parcelRoundTripPreservesFieldSubset() {
        RemoteMedia original = new RemoteMedia("http://example.com/ep.mp3", "item-1", "http://example.com/feed",
                "feedTitle", "episodeTitle", "http://example.com/link", "author",
                "http://example.com/image.png", "http://example.com/feedlink", "audio/mp3",
                new Date(123456789L), "notes");
        original.setDuration(1000);
        original.setPosition(500);
        original.setLastPlayedTimeStatistics(999L);

        RemoteMedia restored = parcelRoundTrip(original);

        assertEquals(original.getDownloadUrl(), restored.getDownloadUrl());
        assertEquals(original.getEpisodeIdentifier(), restored.getEpisodeIdentifier());
        assertEquals(original.getFeedUrl(), restored.getFeedUrl());
        assertEquals(original.getFeedTitle(), restored.getFeedTitle());
        assertEquals(original.getEpisodeTitle(), restored.getEpisodeTitle());
        assertEquals(original.getEpisodeLink(), restored.getEpisodeLink());
        assertEquals(original.getFeedAuthor(), restored.getFeedAuthor());
        assertEquals(original.getImageUrl(), restored.getImageUrl());
        assertEquals(original.getFeedLink(), restored.getFeedLink());
        assertEquals(original.getMimeType(), restored.getMimeType());
        assertEquals(original.getPubDate(), restored.getPubDate());
        assertEquals(original.getNotes(), restored.getNotes());
        assertEquals(original.getDuration(), restored.getDuration());
        assertEquals(original.getPosition(), restored.getPosition());
        assertEquals(original.getLastPlayedTimeStatistics(), restored.getLastPlayedTimeStatistics());
    }

    @Test
    public void equalsAndHashCodeOverThreeFieldsOnly() {
        RemoteMedia a = new RemoteMedia("http://example.com/ep.mp3", "item-1", "http://example.com/feed",
                "feedTitleA", "episodeTitleA", "linkA", "authorA", "imageA", "feedLinkA", "audio/mp3",
                new Date(1L), "notesA");
        RemoteMedia b = new RemoteMedia("http://example.com/ep.mp3", "item-1", "http://example.com/feed",
                "feedTitleB", "episodeTitleB", "linkB", "authorB", "imageB", "feedLinkB", "audio/ogg",
                new Date(2L), "notesB");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        RemoteMedia differentDownloadUrl = new RemoteMedia("http://example.com/other.mp3", "item-1",
                "http://example.com/feed", "feedTitleA", "episodeTitleA", "linkA", "authorA", "imageA",
                "feedLinkA", "audio/mp3", new Date(1L), "notesA");
        assertNotEquals(a, differentDownloadUrl);
    }

    private static RemoteMedia parcelRoundTrip(RemoteMedia original) {
        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        return RemoteMedia.CREATOR.createFromParcel(parcel);
    }
}
