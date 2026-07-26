package de.danoeh.antennapod.event.playback;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SpeedChangedEventTest {

    @Test
    public void newSpeedIsStored() {
        SpeedChangedEvent event = new SpeedChangedEvent(1.5f);
        assertEquals(1.5f, event.getNewSpeed(), 0f);
    }
}
