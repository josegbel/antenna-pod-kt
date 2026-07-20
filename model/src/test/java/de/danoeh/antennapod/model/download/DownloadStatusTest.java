package de.danoeh.antennapod.model.download;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;

public class DownloadStatusTest {

    @Test
    public void testConstructorGetterRoundTrip() {
        DownloadStatus status = new DownloadStatus(DownloadStatus.STATE_RUNNING, 42);

        assertEquals(DownloadStatus.STATE_RUNNING, status.getState());
        assertEquals(42, status.getProgress());
    }

    @Test
    public void testStaticStateConstants() {
        assertEquals(0, DownloadStatus.STATE_QUEUED);
        assertEquals(1, DownloadStatus.STATE_COMPLETED);
        assertEquals(2, DownloadStatus.STATE_RUNNING);
    }

    @Test
    public void testReferenceEqualityPin() {
        DownloadStatus status1 = new DownloadStatus(DownloadStatus.STATE_QUEUED, 0);
        DownloadStatus status2 = new DownloadStatus(DownloadStatus.STATE_QUEUED, 0);

        // No equals()/hashCode() defined: two same-content instances must NOT be equal.
        assertNotSame(status1, status2);
        assertFalse(status1.equals(status2));
    }
}
