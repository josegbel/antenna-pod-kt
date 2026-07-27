package de.danoeh.antennapod.event

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncServiceEventTest {

    @Test
    fun messageResIdIsStored() {
        val event = SyncServiceEvent(42)
        assertEquals(42, event.messageResId)
    }
}
