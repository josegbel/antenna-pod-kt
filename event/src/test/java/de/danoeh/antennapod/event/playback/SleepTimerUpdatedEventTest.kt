package de.danoeh.antennapod.event.playback

import de.danoeh.antennapod.model.playback.TimerValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerUpdatedEventTest {

    @Test
    fun justEnabledNegatesMillisAndKeepsDisplayValue() {
        val event = SleepTimerUpdatedEvent.justEnabled(TimerValue(5L, 1000L))
        assertEquals(5L, event.getDisplayTimeLeft())
        assertEquals(1000L, event.getMillisTimeLeft())
        assertTrue(event.wasJustEnabled())
        assertFalse(event.isOver())
        assertFalse(event.isCancelled())
    }

    @Test
    fun updatedClampsBothComponentsAtZero() {
        val event = SleepTimerUpdatedEvent.updated(TimerValue(-5L, -1000L))
        assertEquals(0L, event.getDisplayTimeLeft())
        assertEquals(0L, event.getMillisTimeLeft())
        assertTrue(event.isOver())
        assertFalse(event.wasJustEnabled())
        assertFalse(event.isCancelled())
    }

    @Test
    fun updatedWithPositiveValuesIsUnaffectedByClamp() {
        val event = SleepTimerUpdatedEvent.updated(TimerValue(7L, 2000L))
        assertEquals(7L, event.getDisplayTimeLeft())
        assertEquals(2000L, event.getMillisTimeLeft())
        assertFalse(event.isOver())
        assertFalse(event.wasJustEnabled())
        assertFalse(event.isCancelled())
    }

    @Test
    fun cancelledSetsBothComponentsToLongMaxValue() {
        val event = SleepTimerUpdatedEvent.cancelled()
        assertTrue(event.isCancelled())
        assertEquals(Long.MAX_VALUE, event.getMillisTimeLeft())
        assertEquals(Long.MAX_VALUE, event.getDisplayTimeLeft())
        assertFalse(event.wasJustEnabled())
        assertFalse(event.isOver())
    }

    @Test
    fun millisTimeLeftIsAbsoluteValueOfNegatedMillis() {
        val event = SleepTimerUpdatedEvent.justEnabled(TimerValue(0L, 12345L))
        assertEquals(12345L, event.getMillisTimeLeft())
    }

    @Test
    fun millisTimeLeftOverflowsBackToLongMinValueWhenNegatingLongMinValue() {
        val event = SleepTimerUpdatedEvent.justEnabled(TimerValue(0L, Long.MIN_VALUE))
        assertEquals(Long.MIN_VALUE, event.getMillisTimeLeft())
    }
}
