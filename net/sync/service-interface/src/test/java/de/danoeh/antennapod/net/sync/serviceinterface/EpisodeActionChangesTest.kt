package de.danoeh.antennapod.net.sync.serviceinterface

import java.util.Collections
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Characterizes [EpisodeActionChanges] against the live Java implementation, including its
 * exact (and misleadingly-named — it does not match the class name) `toString()` format,
 * which reaches `Log.d` in production.
 */
class EpisodeActionChangesTest {

    @Test
    fun constructorRoundTripsEpisodeActionsAndTimestamp() {
        val action = EpisodeAction.Builder("p", "e", EpisodeAction.Action.PLAY).build()
        val actions = Collections.singletonList(action)

        val changes = EpisodeActionChanges(actions, 55L)

        assertEquals(actions, changes.episodeActions)
        assertEquals(55L, changes.timestamp)
    }

    @Test
    fun toStringMatchesExactGoldenFormat() {
        val action = EpisodeAction.Builder("p", "e", EpisodeAction.Action.PLAY).build()
        val actions = Collections.singletonList(action)
        val changes = EpisodeActionChanges(actions, 77L)

        val expected = "EpisodeActionGetResponse{episodeActions=$actions, timestamp=77}"
        assertEquals(expected, changes.toString())
    }
}
