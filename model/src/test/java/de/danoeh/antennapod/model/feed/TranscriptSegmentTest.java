package de.danoeh.antennapod.model.feed;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;

public class TranscriptSegmentTest {

    @Test
    public void testConstructorGetterRoundTrip() {
        TranscriptSegment segment = new TranscriptSegment(1000L, 2000L, "hello world", "Speaker A");

        assertEquals(1000L, segment.getStartTime());
        assertEquals(2000L, segment.getEndTime());
        assertEquals("hello world", segment.getWords());
        assertEquals("Speaker A", segment.getSpeaker());
    }

    @Test
    public void testAppendAdvancesEndTimeAndConcatenatesWithSingleSpace() {
        TranscriptSegment segment = new TranscriptSegment(1000L, 2000L, "hello", "Speaker A");

        segment.append(3000L, "world");

        assertEquals(3000L, segment.getEndTime());
        assertEquals("hello world", segment.getWords());
    }

    @Test
    public void testAppendMultipleTimes() {
        TranscriptSegment segment = new TranscriptSegment(0L, 100L, "one", null);

        segment.append(200L, "two");
        segment.append(300L, "three");

        assertEquals(300L, segment.getEndTime());
        assertEquals("one two three", segment.getWords());
    }

    /**
     * Pins the exact current Java behavior when a TranscriptSegment is seeded with a null
     * "words" value and append() is called: Java's String concatenation coerces the null
     * operand to the literal string "null" (words += " " + wordsToAppend), producing
     * "null foo" rather than throwing or producing " foo"/"foo". This is a live risk named
     * in Resolved Decision 4 — it must be pinned against the unconverted .java before any
     * Kotlin conversion, since Kotlin's String?.plus(Any?) happens to mirror this coercion
     * but that equivalence must be proven, not assumed.
     */
    @Test
    public void testAppendWithNullSeedWords() {
        TranscriptSegment segment = new TranscriptSegment(0L, 100L, null, null);

        segment.append(200L, "foo");

        assertEquals("null foo", segment.getWords());
    }

    @Test
    public void testReferenceEqualityPin() {
        TranscriptSegment segment1 = new TranscriptSegment(1000L, 2000L, "hello", "Speaker A");
        TranscriptSegment segment2 = new TranscriptSegment(1000L, 2000L, "hello", "Speaker A");

        // Two same-content instances must NOT be equal: no equals()/hashCode() is defined,
        // so this pins plain JVM reference equality (must survive as plain `class`, not `data class`).
        assertNotSame(segment1, segment2);
        assertFalse(segment1.equals(segment2));
    }
}
