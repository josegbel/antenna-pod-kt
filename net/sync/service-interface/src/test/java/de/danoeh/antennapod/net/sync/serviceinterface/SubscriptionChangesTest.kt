package de.danoeh.antennapod.net.sync.serviceinterface

import java.util.Arrays
import java.util.Collections
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Characterizes [SubscriptionChanges] against the live Java implementation, including its
 * exact (and misleadingly-named, singular "Change") `toString()` format, which reaches
 * `Log.d` in production.
 */
class SubscriptionChangesTest {

    @Test
    fun constructorRoundTripsAddedRemovedAndTimestamp() {
        val added = Arrays.asList("feed-1", "feed-2")
        val removed = Collections.singletonList("feed-3")

        val changes = SubscriptionChanges(added, removed, 123L)

        assertEquals(added, changes.added)
        assertEquals(removed, changes.removed)
        assertEquals(123L, changes.timestamp)
    }

    @Test
    fun toStringMatchesExactGoldenFormat() {
        val added = Collections.singletonList("feed-1")
        val removed = Collections.singletonList("feed-2")
        val changes = SubscriptionChanges(added, removed, 42L)

        val expected = "SubscriptionChange [added=$added, removed=$removed, timestamp=42]"
        assertEquals(expected, changes.toString())
    }
}
