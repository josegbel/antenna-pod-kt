package de.danoeh.antennapod.event

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerErrorEventTest {

    @Test
    fun messageIsStored() {
        val event = PlayerErrorEvent("boom")
        assertEquals("boom", event.message)
    }

    @Test
    fun nullMessageIsStoredWithoutThrowing() {
        val event = PlayerErrorEvent(null)
        assertNull(event.message)
    }
}
