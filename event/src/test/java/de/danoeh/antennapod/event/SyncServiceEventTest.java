package de.danoeh.antennapod.event;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SyncServiceEventTest {

    @Test
    public void messageResIdIsStored() {
        SyncServiceEvent event = new SyncServiceEvent(42);
        assertEquals(42, event.getMessageResId());
    }
}
