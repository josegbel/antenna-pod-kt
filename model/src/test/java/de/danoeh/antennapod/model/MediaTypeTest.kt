package de.danoeh.antennapod.model

import de.danoeh.antennapod.model.playback.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaTypeTest {

    @Test
    fun fromMimeTypeNullReturnsUnknown() {
        assertEquals(MediaType.UNKNOWN, MediaType.fromMimeType(null))
    }

    @Test
    fun fromMimeTypeEmptyReturnsUnknown() {
        assertEquals(MediaType.UNKNOWN, MediaType.fromMimeType(""))
    }

    @Test
    fun fromMimeTypeAudioPrefixReturnsAudio() {
        assertEquals(MediaType.AUDIO, MediaType.fromMimeType("audio/mpeg"))
    }

    @Test
    fun fromMimeTypeVideoPrefixReturnsVideo() {
        assertEquals(MediaType.VIDEO, MediaType.fromMimeType("video/mp4"))
    }

    @Test
    fun fromMimeTypeApplicationOggReturnsAudio() {
        assertEquals(MediaType.AUDIO, MediaType.fromMimeType("application/ogg"))
    }

    @Test
    fun fromMimeTypeApplicationOpusReturnsAudio() {
        assertEquals(MediaType.AUDIO, MediaType.fromMimeType("application/opus"))
    }

    @Test
    fun fromMimeTypeApplicationXFlacReturnsAudio() {
        assertEquals(MediaType.AUDIO, MediaType.fromMimeType("application/x-flac"))
    }

    @Test
    fun fromMimeTypeUnknownApplicationReturnsUnknown() {
        assertEquals(MediaType.UNKNOWN, MediaType.fromMimeType("application/pdf"))
    }
}
