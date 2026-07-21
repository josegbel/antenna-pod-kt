package de.danoeh.antennapod.model.feed;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TranscriptTest {

    private TranscriptSegment segmentAt(long startTime, long endTime) {
        return new TranscriptSegment(startTime, endTime, "words", "Speaker A");
    }

    @Test
    public void testAddSegmentInOrderAccepted() {
        Transcript transcript = new Transcript();

        transcript.addSegment(segmentAt(0, 100));
        transcript.addSegment(segmentAt(100, 200));
        transcript.addSegment(segmentAt(200, 300));

        assertEquals(3, transcript.getSegmentCount());
    }

    @Test
    public void testAddSegmentOutOfOrderRejected() {
        Transcript transcript = new Transcript();
        transcript.addSegment(segmentAt(100, 200));

        try {
            transcript.addSegment(segmentAt(50, 150));
            fail("Expected an IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void testAddSegmentWithDuplicateStartTimeRejected() {
        Transcript transcript = new Transcript();
        transcript.addSegment(segmentAt(100, 200));

        try {
            transcript.addSegment(segmentAt(100, 300));
            fail("Expected an IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void testFindSegmentIndexBeforeAndGetSegmentAtTime() {
        Transcript transcript = new Transcript();
        TranscriptSegment first = segmentAt(0, 100);
        TranscriptSegment second = segmentAt(100, 200);
        TranscriptSegment third = segmentAt(200, 300);
        TranscriptSegment fourth = segmentAt(300, 400);
        transcript.addSegment(first);
        transcript.addSegment(second);
        transcript.addSegment(third);
        transcript.addSegment(fourth);

        // Before the first segment's start time still resolves to index 0 (binary search
        // floor semantics, not a bounds check).
        assertEquals(0, transcript.findSegmentIndexBefore(-50));
        assertEquals(first, transcript.getSegmentAtTime(-50));

        assertEquals(0, transcript.findSegmentIndexBefore(50));
        assertEquals(first, transcript.getSegmentAtTime(50));

        assertEquals(1, transcript.findSegmentIndexBefore(150));
        assertEquals(second, transcript.getSegmentAtTime(150));

        assertEquals(2, transcript.findSegmentIndexBefore(250));
        assertEquals(third, transcript.getSegmentAtTime(250));

        // After the last segment's start time still resolves to the last index.
        assertEquals(3, transcript.findSegmentIndexBefore(999));
        assertEquals(fourth, transcript.getSegmentAtTime(999));
    }

    @Test
    public void testGetSegmentCount() {
        Transcript transcript = new Transcript();
        assertEquals(0, transcript.getSegmentCount());

        transcript.addSegment(segmentAt(0, 100));

        assertEquals(1, transcript.getSegmentCount());
    }

    @Test
    public void testSpeakersIsNullUntilSet() {
        Transcript transcript = new Transcript();

        assertNull(transcript.getSpeakers());
    }

    @Test
    public void testSetSpeakersGetSpeakersRoundTrip() {
        Transcript transcript = new Transcript();
        Set<String> speakers = new HashSet<>();
        speakers.add("Speaker A");
        speakers.add("Speaker B");

        transcript.setSpeakers(speakers);

        assertEquals(speakers, transcript.getSpeakers());
    }

    @Test
    public void testReferenceEqualityPin() {
        Transcript transcript1 = new Transcript();
        Transcript transcript2 = new Transcript();

        // No equals()/hashCode() defined: two same-content instances must NOT be equal.
        assertNotSame(transcript1, transcript2);
        assertFalse(transcript1.equals(transcript2));
    }
}
