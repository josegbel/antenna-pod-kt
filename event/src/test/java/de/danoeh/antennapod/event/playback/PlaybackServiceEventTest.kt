package de.danoeh.antennapod.event.playback

import org.junit.Assert.assertSame
import org.junit.Test

class PlaybackServiceEventTest {

    @Test
    fun serviceStartedActionIsStored() {
        val event = PlaybackServiceEvent(PlaybackServiceEvent.Action.SERVICE_STARTED)
        assertSame(PlaybackServiceEvent.Action.SERVICE_STARTED, event.action)
    }

    @Test
    fun serviceShutDownActionIsStored() {
        val event = PlaybackServiceEvent(PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN)
        assertSame(PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN, event.action)
    }
}
