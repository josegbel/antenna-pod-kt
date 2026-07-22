package de.danoeh.antennapod.model;

import de.danoeh.antennapod.model.playback.MediaType;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MediaTypeTest {

    @Test
    public void fromMimeTypeNullReturnsUnknown() {
        assertEquals(MediaType.UNKNOWN, MediaType.fromMimeType(null));
    }

    @Test
    public void fromMimeTypeEmptyReturnsUnknown() {
        assertEquals(MediaType.UNKNOWN, MediaType.fromMimeType(""));
    }

    @Test
    public void fromMimeTypeAudioPrefixReturnsAudio() {
        assertEquals(MediaType.AUDIO, MediaType.fromMimeType("audio/mpeg"));
    }

    @Test
    public void fromMimeTypeVideoPrefixReturnsVideo() {
        assertEquals(MediaType.VIDEO, MediaType.fromMimeType("video/mp4"));
    }

    @Test
    public void fromMimeTypeApplicationOggReturnsAudio() {
        assertEquals(MediaType.AUDIO, MediaType.fromMimeType("application/ogg"));
    }

    @Test
    public void fromMimeTypeApplicationOpusReturnsAudio() {
        assertEquals(MediaType.AUDIO, MediaType.fromMimeType("application/opus"));
    }

    @Test
    public void fromMimeTypeApplicationXFlacReturnsAudio() {
        assertEquals(MediaType.AUDIO, MediaType.fromMimeType("application/x-flac"));
    }

    @Test
    public void fromMimeTypeUnknownApplicationReturnsUnknown() {
        assertEquals(MediaType.UNKNOWN, MediaType.fromMimeType("application/pdf"));
    }
}
