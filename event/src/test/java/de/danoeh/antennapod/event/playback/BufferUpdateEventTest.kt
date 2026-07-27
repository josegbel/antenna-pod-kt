package de.danoeh.antennapod.event.playback

import java.lang.Float
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BufferUpdateEventTest {

    @Test
    fun startedSetsHasStartedAndNotHasEnded() {
        val event = BufferUpdateEvent.started()
        assertEquals(-1f, event.progress, 0f)
        assertTrue(event.hasStarted())
        assertFalse(event.hasEnded())
    }

    @Test
    fun endedSetsHasEndedAndNotHasStarted() {
        val event = BufferUpdateEvent.ended()
        assertEquals(-2f, event.progress, 0f)
        assertFalse(event.hasStarted())
        assertTrue(event.hasEnded())
    }

    @Test
    fun progressUpdateStoresValueAndIsNeitherStartedNorEnded() {
        val event = BufferUpdateEvent.progressUpdate(0.5f)
        assertEquals(0.5f, event.progress, 0f)
        assertFalse(event.hasStarted())
        assertFalse(event.hasEnded())
    }

    @Test
    fun progressUpdateOfNegativeOneMasqueradesAsStarted() {
        val event = BufferUpdateEvent.progressUpdate(-1f)
        assertTrue(event.hasStarted())
        assertFalse(event.hasEnded())
    }

    @Test
    fun progressUpdateOfNanIsNeitherStartedNorEnded() {
        val event = BufferUpdateEvent.progressUpdate(Float.NaN)
        assertTrue(Float.isNaN(event.progress))
        assertFalse(event.hasStarted())
        assertFalse(event.hasEnded())
    }

    @Test
    fun progressUpdateOfNegativeZeroIsNeitherStartedNorEnded() {
        val event = BufferUpdateEvent.progressUpdate(-0.0f)
        assertEquals(-0.0f, event.progress, 0f)
        assertFalse(event.hasStarted())
        assertFalse(event.hasEnded())
    }

    @Test
    fun progressUpdateOfPositiveZeroIsNeitherStartedNorEnded() {
        val event = BufferUpdateEvent.progressUpdate(0.0f)
        assertEquals(0.0f, event.progress, 0f)
        assertFalse(event.hasStarted())
        assertFalse(event.hasEnded())
    }
}
