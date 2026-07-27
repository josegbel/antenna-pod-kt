package de.danoeh.antennapod.event

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class DownloadLogEventTest {

    @Test
    fun listUpdatedReturnsNewInstanceEachCall() {
        val first = DownloadLogEvent.listUpdated()
        val second = DownloadLogEvent.listUpdated()
        assertNotSame(first, second)
    }

    @Test
    fun toStringIsFixedString() {
        assertEquals("DownloadLogEvent", DownloadLogEvent.listUpdated().toString())
    }
}
