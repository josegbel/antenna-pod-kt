package de.danoeh.antennapod.model.feed;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TranscriptTypeTest {

    @Test
    public void testPriorityAndCanonicalMimePerConstant() {
        assertEquals(4, TranscriptType.JSON.priority);
        assertEquals("application/json", TranscriptType.JSON.canonicalMime);

        assertEquals(3, TranscriptType.VTT.priority);
        assertEquals("text/vtt", TranscriptType.VTT.canonicalMime);

        assertEquals(2, TranscriptType.SRT.priority);
        assertEquals("application/srt", TranscriptType.SRT.canonicalMime);

        assertEquals(0, TranscriptType.NONE.priority);
        assertEquals("", TranscriptType.NONE.canonicalMime);
    }

    @Test
    public void testFromMimeExactMatches() {
        assertEquals(TranscriptType.JSON, TranscriptType.fromMime("application/json"));
        assertEquals(TranscriptType.VTT, TranscriptType.fromMime("text/vtt"));
        assertEquals(TranscriptType.SRT, TranscriptType.fromMime("application/srt"));
    }

    @Test
    public void testFromMimeAliases() {
        assertEquals(TranscriptType.SRT, TranscriptType.fromMime("application/srr"));
        assertEquals(TranscriptType.SRT, TranscriptType.fromMime("application/x-subrip"));
    }

    @Test
    public void testFromMimeNullReturnsNone() {
        assertEquals(TranscriptType.NONE, TranscriptType.fromMime(null));
    }

    @Test
    public void testFromMimeUnknownReturnsNone() {
        assertEquals(TranscriptType.NONE, TranscriptType.fromMime("application/unknown"));
        assertEquals(TranscriptType.NONE, TranscriptType.fromMime(""));
    }
}
