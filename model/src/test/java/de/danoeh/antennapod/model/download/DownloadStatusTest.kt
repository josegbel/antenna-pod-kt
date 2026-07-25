package de.danoeh.antennapod.model.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Test

class DownloadStatusTest {

    @Test
    fun testConstructorGetterRoundTrip() {
        val status = DownloadStatus(DownloadStatus.STATE_RUNNING, 42)

        assertEquals(DownloadStatus.STATE_RUNNING, status.getState())
        assertEquals(42, status.getProgress())
    }

    @Test
    fun testStaticStateConstants() {
        assertEquals(0, DownloadStatus.STATE_QUEUED)
        assertEquals(1, DownloadStatus.STATE_COMPLETED)
        assertEquals(2, DownloadStatus.STATE_RUNNING)
    }

    @Test
    fun testReferenceEqualityPin() {
        val status1 = DownloadStatus(DownloadStatus.STATE_QUEUED, 0)
        val status2 = DownloadStatus(DownloadStatus.STATE_QUEUED, 0)

        // No equals()/hashCode() defined: two same-content instances must NOT be equal.
        assertNotSame(status1, status2)
        assertFalse(status1.equals(status2))
    }
}
