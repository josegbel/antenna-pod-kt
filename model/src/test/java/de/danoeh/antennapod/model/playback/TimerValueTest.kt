package de.danoeh.antennapod.model.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Test

class TimerValueTest {

    @Test
    fun testConstructorGetterRoundTrip() {
        val value = TimerValue(5L, 300000L)

        assertEquals(5L, value.getDisplayValue())
        assertEquals(300000L, value.getMillisValue())
    }

    @Test
    fun testReferenceEqualityPin() {
        val value1 = TimerValue(5L, 300000L)
        val value2 = TimerValue(5L, 300000L)

        // No equals()/hashCode() defined: two same-content instances must NOT be equal.
        assertNotSame(value1, value2)
        assertFalse(value1.equals(value2))
    }
}
