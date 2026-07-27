package de.danoeh.antennapod.event

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedUpdateRunningEventTest {

    @Test
    fun trueIsStoredAndReadBack() {
        val event = FeedUpdateRunningEvent(true)
        assertTrue(event.isFeedUpdateRunning)
    }

    @Test
    fun falseIsStoredAndReadBack() {
        val event = FeedUpdateRunningEvent(false)
        assertFalse(event.isFeedUpdateRunning)
    }
}
