package de.danoeh.antennapod.event;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PlayerErrorEventTest {

    @Test
    public void messageIsStored() {
        PlayerErrorEvent event = new PlayerErrorEvent("boom");
        assertEquals("boom", event.getMessage());
    }

    @Test
    public void nullMessageIsStoredWithoutThrowing() {
        PlayerErrorEvent event = new PlayerErrorEvent(null);
        assertNull(event.getMessage());
    }
}
