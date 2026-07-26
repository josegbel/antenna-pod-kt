package de.danoeh.antennapod.event.playback;

import org.junit.Test;

import static org.junit.Assert.assertSame;

public class PlaybackServiceEventTest {

    @Test
    public void serviceStartedActionIsStored() {
        PlaybackServiceEvent event = new PlaybackServiceEvent(PlaybackServiceEvent.Action.SERVICE_STARTED);
        assertSame(PlaybackServiceEvent.Action.SERVICE_STARTED, event.action);
    }

    @Test
    public void serviceShutDownActionIsStored() {
        PlaybackServiceEvent event = new PlaybackServiceEvent(PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN);
        assertSame(PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN, event.action);
    }
}
