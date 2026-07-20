package de.danoeh.antennapod.model.playback;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;

public class TimerValueTest {

    @Test
    public void testConstructorGetterRoundTrip() {
        TimerValue value = new TimerValue(5L, 300000L);

        assertEquals(5L, value.getDisplayValue());
        assertEquals(300000L, value.getMillisValue());
    }

    @Test
    public void testReferenceEqualityPin() {
        TimerValue value1 = new TimerValue(5L, 300000L);
        TimerValue value2 = new TimerValue(5L, 300000L);

        // No equals()/hashCode() defined: two same-content instances must NOT be equal.
        assertNotSame(value1, value2);
        assertFalse(value1.equals(value2));
    }
}
