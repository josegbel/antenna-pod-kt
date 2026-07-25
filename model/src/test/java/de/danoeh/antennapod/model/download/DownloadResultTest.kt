package de.danoeh.antennapod.model.download

import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadResultTest {

    @Test
    fun testSixArgConstructorGetterRoundTrip() {
        val result = DownloadResult("My Title", 42L, 1, true, DownloadError.SUCCESS, null)

        assertEquals("My Title", result.getTitle())
        assertEquals(42L, result.getFeedfileId())
        assertEquals(1, result.getFeedfileType())
        assertTrue(result.isSuccessful())
        assertEquals(DownloadError.SUCCESS, result.getReason())
        assertNull(result.getReasonDetailed())
        assertEquals(0L, result.getId())
    }

    @Test
    fun testEightArgConstructorGetterRoundTrip() {
        val completionDate = Date(1000L)
        val result = DownloadResult(
            7L,
            "Other Title",
            99L,
            2,
            false,
            DownloadError.ERROR_IO_ERROR,
            completionDate,
            "disk full"
        )

        assertEquals(7L, result.getId())
        assertEquals("Other Title", result.getTitle())
        assertEquals(99L, result.getFeedfileId())
        assertEquals(2, result.getFeedfileType())
        assertFalse(result.isSuccessful())
        assertEquals(DownloadError.ERROR_IO_ERROR, result.getReason())
        assertEquals("disk full", result.getReasonDetailed())
        assertEquals(completionDate, result.getCompletionDate())
    }

    @Test
    fun testToStringPinnedVerbatim() {
        val completionDate = Date(1000L)
        val result = DownloadResult(
            7L,
            "Title",
            99L,
            2,
            false,
            DownloadError.ERROR_IO_ERROR,
            completionDate,
            "detail"
        )

        val expected = "DownloadStatus [id=7, title=Title, reason=" + DownloadError.ERROR_IO_ERROR +
            ", reasonDetailed=detail, successful=false, completionDate=" + completionDate +
            ", feedfileId=99, feedfileType=2]"
        assertEquals(expected, result.toString())
    }

    @Test
    fun testCompletionDateIsDefensivelyCloned() {
        val completionDate = Date(1000L)
        val result = DownloadResult(
            0L,
            "Title",
            1L,
            1,
            true,
            DownloadError.SUCCESS,
            completionDate,
            null
        )

        // Mutating the Date passed into the constructor must not affect the stored value.
        completionDate.time = 2000L
        assertEquals(1000L, result.getCompletionDate().time)

        // Mutating the Date returned by getCompletionDate() must not affect internal state.
        val returned = result.getCompletionDate()
        returned.time = 3000L
        assertEquals(1000L, result.getCompletionDate().time)
    }

    @Test
    fun testSetSuccessful() {
        val result = DownloadResult(
            "Title",
            1L,
            1,
            false,
            DownloadError.ERROR_IO_ERROR,
            "detail"
        )

        result.setSuccessful()

        assertTrue(result.isSuccessful())
        assertEquals(DownloadError.SUCCESS, result.getReason())
    }

    @Test
    fun testSetFailed() {
        val result = DownloadResult(
            "Title",
            1L,
            1,
            true,
            DownloadError.SUCCESS,
            null
        )

        result.setFailed(DownloadError.ERROR_UNAUTHORIZED, "not authorized")

        assertFalse(result.isSuccessful())
        assertEquals(DownloadError.ERROR_UNAUTHORIZED, result.getReason())
        assertEquals("not authorized", result.getReasonDetailed())
    }

    @Test
    fun testSetCancelled() {
        val result = DownloadResult(
            "Title",
            1L,
            1,
            true,
            DownloadError.SUCCESS,
            null
        )

        result.setCancelled()

        assertFalse(result.isSuccessful())
        assertEquals(DownloadError.ERROR_DOWNLOAD_CANCELLED, result.getReason())
    }

    @Test
    fun testSetId() {
        val result = DownloadResult(
            "Title",
            1L,
            1,
            true,
            DownloadError.SUCCESS,
            null
        )

        result.setId(123L)

        assertEquals(123L, result.getId())
    }

    @Test
    fun testNullReasonAndReasonDetailed() {
        // Mirrors the real Downloader.java call site which passes null for both fields.
        val result = DownloadResult(
            0,
            "Title",
            1L,
            1,
            false,
            null,
            Date(),
            null
        )

        assertNull(result.getReason())
        assertNull(result.getReasonDetailed())
    }

    @Test
    fun testSizeUnknownConstant() {
        assertEquals(-1, DownloadResult.SIZE_UNKNOWN)
    }

    @Test
    fun testReferenceEqualityPin() {
        val result1 = DownloadResult("Title", 1L, 1, true, DownloadError.SUCCESS, null)
        val result2 = DownloadResult("Title", 1L, 1, true, DownloadError.SUCCESS, null)

        // No equals()/hashCode() defined: two same-content instances must NOT be equal.
        assertNotSame(result1, result2)
        assertFalse(result1.equals(result2))
    }
}
