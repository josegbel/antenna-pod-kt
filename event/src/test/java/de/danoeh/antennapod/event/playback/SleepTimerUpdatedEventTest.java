package de.danoeh.antennapod.event.playback;

import org.junit.Test;

import de.danoeh.antennapod.model.playback.TimerValue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SleepTimerUpdatedEventTest {

    @Test
    public void justEnabledNegatesMillisAndKeepsDisplayValue() {
        SleepTimerUpdatedEvent event = SleepTimerUpdatedEvent.justEnabled(new TimerValue(5L, 1000L));
        assertEquals(5L, event.getDisplayTimeLeft());
        assertEquals(1000L, event.getMillisTimeLeft());
        assertTrue(event.wasJustEnabled());
        assertFalse(event.isOver());
        assertFalse(event.isCancelled());
    }

    @Test
    public void updatedClampsBothComponentsAtZero() {
        SleepTimerUpdatedEvent event = SleepTimerUpdatedEvent.updated(new TimerValue(-5L, -1000L));
        assertEquals(0L, event.getDisplayTimeLeft());
        assertEquals(0L, event.getMillisTimeLeft());
        assertTrue(event.isOver());
        assertFalse(event.wasJustEnabled());
        assertFalse(event.isCancelled());
    }

    @Test
    public void updatedWithPositiveValuesIsUnaffectedByClamp() {
        SleepTimerUpdatedEvent event = SleepTimerUpdatedEvent.updated(new TimerValue(7L, 2000L));
        assertEquals(7L, event.getDisplayTimeLeft());
        assertEquals(2000L, event.getMillisTimeLeft());
        assertFalse(event.isOver());
        assertFalse(event.wasJustEnabled());
        assertFalse(event.isCancelled());
    }

    @Test
    public void cancelledSetsBothComponentsToLongMaxValue() {
        SleepTimerUpdatedEvent event = SleepTimerUpdatedEvent.cancelled();
        assertTrue(event.isCancelled());
        assertEquals(Long.MAX_VALUE, event.getMillisTimeLeft());
        assertEquals(Long.MAX_VALUE, event.getDisplayTimeLeft());
        assertFalse(event.wasJustEnabled());
        assertFalse(event.isOver());
    }

    @Test
    public void millisTimeLeftIsAbsoluteValueOfNegatedMillis() {
        SleepTimerUpdatedEvent event = SleepTimerUpdatedEvent.justEnabled(new TimerValue(0L, 12345L));
        assertEquals(12345L, event.getMillisTimeLeft());
    }

    @Test
    public void millisTimeLeftOverflowsBackToLongMinValueWhenNegatingLongMinValue() {
        SleepTimerUpdatedEvent event = SleepTimerUpdatedEvent.justEnabled(new TimerValue(0L, Long.MIN_VALUE));
        assertEquals(Long.MIN_VALUE, event.getMillisTimeLeft());
    }
}
