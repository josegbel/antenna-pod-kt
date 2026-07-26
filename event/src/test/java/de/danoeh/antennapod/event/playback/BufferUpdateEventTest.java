package de.danoeh.antennapod.event.playback;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BufferUpdateEventTest {

    @Test
    public void startedSetsHasStartedAndNotHasEnded() {
        BufferUpdateEvent event = BufferUpdateEvent.started();
        assertEquals(-1f, event.getProgress(), 0f);
        assertTrue(event.hasStarted());
        assertFalse(event.hasEnded());
    }

    @Test
    public void endedSetsHasEndedAndNotHasStarted() {
        BufferUpdateEvent event = BufferUpdateEvent.ended();
        assertEquals(-2f, event.getProgress(), 0f);
        assertFalse(event.hasStarted());
        assertTrue(event.hasEnded());
    }

    @Test
    public void progressUpdateStoresValueAndIsNeitherStartedNorEnded() {
        BufferUpdateEvent event = BufferUpdateEvent.progressUpdate(0.5f);
        assertEquals(0.5f, event.getProgress(), 0f);
        assertFalse(event.hasStarted());
        assertFalse(event.hasEnded());
    }

    @Test
    public void progressUpdateOfNegativeOneMasqueradesAsStarted() {
        BufferUpdateEvent event = BufferUpdateEvent.progressUpdate(-1f);
        assertTrue(event.hasStarted());
        assertFalse(event.hasEnded());
    }

    @Test
    public void progressUpdateOfNanIsNeitherStartedNorEnded() {
        BufferUpdateEvent event = BufferUpdateEvent.progressUpdate(Float.NaN);
        assertTrue(Float.isNaN(event.getProgress()));
        assertFalse(event.hasStarted());
        assertFalse(event.hasEnded());
    }

    @Test
    public void progressUpdateOfNegativeZeroIsNeitherStartedNorEnded() {
        BufferUpdateEvent event = BufferUpdateEvent.progressUpdate(-0.0f);
        assertEquals(-0.0f, event.getProgress(), 0f);
        assertFalse(event.hasStarted());
        assertFalse(event.hasEnded());
    }

    @Test
    public void progressUpdateOfPositiveZeroIsNeitherStartedNorEnded() {
        BufferUpdateEvent event = BufferUpdateEvent.progressUpdate(0.0f);
        assertEquals(0.0f, event.getProgress(), 0f);
        assertFalse(event.hasStarted());
        assertFalse(event.hasEnded());
    }
}
