package de.danoeh.antennapod.event.playback;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

public class PlaybackHistoryEventTest {

    @Test
    public void listUpdatedReturnsNewInstanceEachCall() {
        PlaybackHistoryEvent first = PlaybackHistoryEvent.listUpdated();
        PlaybackHistoryEvent second = PlaybackHistoryEvent.listUpdated();
        assertNotSame(first, second);
    }

    @Test
    public void toStringIsFixedString() {
        assertEquals("PlaybackHistoryEvent", PlaybackHistoryEvent.listUpdated().toString());
    }
}
