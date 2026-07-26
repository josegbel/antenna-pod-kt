package de.danoeh.antennapod.event.playback;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PlaybackPositionEventTest {

    @Test
    public void positionAndDurationAreStored() {
        PlaybackPositionEvent event = new PlaybackPositionEvent(30, 120);
        assertEquals(30, event.getPosition());
        assertEquals(120, event.getDuration());
    }
}
