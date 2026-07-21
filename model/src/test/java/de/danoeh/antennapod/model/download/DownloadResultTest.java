package de.danoeh.antennapod.model.download;

import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DownloadResultTest {

    @Test
    public void testSixArgConstructorGetterRoundTrip() {
        DownloadResult result = new DownloadResult("My Title", 42L, 1, true, DownloadError.SUCCESS, null);

        assertEquals("My Title", result.getTitle());
        assertEquals(42L, result.getFeedfileId());
        assertEquals(1, result.getFeedfileType());
        assertTrue(result.isSuccessful());
        assertEquals(DownloadError.SUCCESS, result.getReason());
        assertNull(result.getReasonDetailed());
        assertEquals(0L, result.getId());
    }

    @Test
    public void testEightArgConstructorGetterRoundTrip() {
        Date completionDate = new Date(1000L);
        DownloadResult result = new DownloadResult(7L, "Other Title", 99L, 2, false,
                DownloadError.ERROR_IO_ERROR, completionDate, "disk full");

        assertEquals(7L, result.getId());
        assertEquals("Other Title", result.getTitle());
        assertEquals(99L, result.getFeedfileId());
        assertEquals(2, result.getFeedfileType());
        assertFalse(result.isSuccessful());
        assertEquals(DownloadError.ERROR_IO_ERROR, result.getReason());
        assertEquals("disk full", result.getReasonDetailed());
        assertEquals(completionDate, result.getCompletionDate());
    }

    @Test
    public void testToStringPinnedVerbatim() {
        Date completionDate = new Date(1000L);
        DownloadResult result = new DownloadResult(7L, "Title", 99L, 2, false,
                DownloadError.ERROR_IO_ERROR, completionDate, "detail");

        String expected = "DownloadStatus [id=7, title=Title, reason=" + DownloadError.ERROR_IO_ERROR
                + ", reasonDetailed=detail, successful=false, completionDate=" + completionDate
                + ", feedfileId=99, feedfileType=2]";
        assertEquals(expected, result.toString());
    }

    @Test
    public void testCompletionDateIsDefensivelyCloned() {
        Date completionDate = new Date(1000L);
        DownloadResult result = new DownloadResult(0L, "Title", 1L, 1, true,
                DownloadError.SUCCESS, completionDate, null);

        // Mutating the Date passed into the constructor must not affect the stored value.
        completionDate.setTime(2000L);
        assertEquals(1000L, result.getCompletionDate().getTime());

        // Mutating the Date returned by getCompletionDate() must not affect internal state.
        Date returned = result.getCompletionDate();
        returned.setTime(3000L);
        assertEquals(1000L, result.getCompletionDate().getTime());
    }

    @Test
    public void testSetSuccessful() {
        DownloadResult result = new DownloadResult("Title", 1L, 1, false,
                DownloadError.ERROR_IO_ERROR, "detail");

        result.setSuccessful();

        assertTrue(result.isSuccessful());
        assertEquals(DownloadError.SUCCESS, result.getReason());
    }

    @Test
    public void testSetFailed() {
        DownloadResult result = new DownloadResult("Title", 1L, 1, true,
                DownloadError.SUCCESS, null);

        result.setFailed(DownloadError.ERROR_UNAUTHORIZED, "not authorized");

        assertFalse(result.isSuccessful());
        assertEquals(DownloadError.ERROR_UNAUTHORIZED, result.getReason());
        assertEquals("not authorized", result.getReasonDetailed());
    }

    @Test
    public void testSetCancelled() {
        DownloadResult result = new DownloadResult("Title", 1L, 1, true,
                DownloadError.SUCCESS, null);

        result.setCancelled();

        assertFalse(result.isSuccessful());
        assertEquals(DownloadError.ERROR_DOWNLOAD_CANCELLED, result.getReason());
    }

    @Test
    public void testSetId() {
        DownloadResult result = new DownloadResult("Title", 1L, 1, true,
                DownloadError.SUCCESS, null);

        result.setId(123L);

        assertEquals(123L, result.getId());
    }

    @Test
    public void testNullReasonAndReasonDetailed() {
        // Mirrors the real Downloader.java call site which passes null for both fields.
        DownloadResult result = new DownloadResult(0, "Title", 1L, 1, false,
                null, new Date(), null);

        assertNull(result.getReason());
        assertNull(result.getReasonDetailed());
    }

    @Test
    public void testSizeUnknownConstant() {
        assertEquals(-1, DownloadResult.SIZE_UNKNOWN);
    }

    @Test
    public void testReferenceEqualityPin() {
        DownloadResult result1 = new DownloadResult("Title", 1L, 1, true, DownloadError.SUCCESS, null);
        DownloadResult result2 = new DownloadResult("Title", 1L, 1, true, DownloadError.SUCCESS, null);

        // No equals()/hashCode() defined: two same-content instances must NOT be equal.
        assertNotSame(result1, result2);
        assertFalse(result1.equals(result2));
    }
}
