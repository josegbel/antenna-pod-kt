package de.danoeh.antennapod.model.feed

import java.util.HashSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class TranscriptTest {

    private fun segmentAt(startTime: Long, endTime: Long): TranscriptSegment {
        return TranscriptSegment(startTime, endTime, "words", "Speaker A")
    }

    @Test
    fun testAddSegmentInOrderAccepted() {
        val transcript = Transcript()

        transcript.addSegment(segmentAt(0, 100))
        transcript.addSegment(segmentAt(100, 200))
        transcript.addSegment(segmentAt(200, 300))

        assertEquals(3, transcript.getSegmentCount())
    }

    @Test
    fun testAddSegmentOutOfOrderRejected() {
        val transcript = Transcript()
        transcript.addSegment(segmentAt(100, 200))

        try {
            transcript.addSegment(segmentAt(50, 150))
            fail("Expected an IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun testAddSegmentWithDuplicateStartTimeRejected() {
        val transcript = Transcript()
        transcript.addSegment(segmentAt(100, 200))

        try {
            transcript.addSegment(segmentAt(100, 300))
            fail("Expected an IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun testFindSegmentIndexBeforeAndGetSegmentAtTime() {
        val transcript = Transcript()
        val first = segmentAt(0, 100)
        val second = segmentAt(100, 200)
        val third = segmentAt(200, 300)
        val fourth = segmentAt(300, 400)
        transcript.addSegment(first)
        transcript.addSegment(second)
        transcript.addSegment(third)
        transcript.addSegment(fourth)

        // Before the first segment's start time still resolves to index 0 (binary search
        // floor semantics, not a bounds check).
        assertEquals(0, transcript.findSegmentIndexBefore(-50))
        assertEquals(first, transcript.getSegmentAtTime(-50))

        assertEquals(0, transcript.findSegmentIndexBefore(50))
        assertEquals(first, transcript.getSegmentAtTime(50))

        assertEquals(1, transcript.findSegmentIndexBefore(150))
        assertEquals(second, transcript.getSegmentAtTime(150))

        assertEquals(2, transcript.findSegmentIndexBefore(250))
        assertEquals(third, transcript.getSegmentAtTime(250))

        // After the last segment's start time still resolves to the last index.
        assertEquals(3, transcript.findSegmentIndexBefore(999))
        assertEquals(fourth, transcript.getSegmentAtTime(999))
    }

    @Test
    fun testGetSegmentCount() {
        val transcript = Transcript()
        assertEquals(0, transcript.getSegmentCount())

        transcript.addSegment(segmentAt(0, 100))

        assertEquals(1, transcript.getSegmentCount())
    }

    @Test
    fun testSpeakersIsNullUntilSet() {
        val transcript = Transcript()

        assertNull(transcript.getSpeakers())
    }

    @Test
    fun testSetSpeakersGetSpeakersRoundTrip() {
        val transcript = Transcript()
        val speakers: MutableSet<String> = HashSet()
        speakers.add("Speaker A")
        speakers.add("Speaker B")

        transcript.setSpeakers(speakers)

        assertEquals(speakers, transcript.getSpeakers())
    }

    @Test
    fun testReferenceEqualityPin() {
        val transcript1 = Transcript()
        val transcript2 = Transcript()

        // No equals()/hashCode() defined: two same-content instances must NOT be equal.
        assertNotSame(transcript1, transcript2)
        assertFalse(transcript1.equals(transcript2))
    }
}
