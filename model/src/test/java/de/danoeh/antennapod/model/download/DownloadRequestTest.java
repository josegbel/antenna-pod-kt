package de.danoeh.antennapod.model.download;

import android.os.Bundle;
import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

// Characterizes DownloadRequest's Parcelable behavior against the live Java implementation,
// under Robolectric (this milestone's disclosed one-milestone exception to :model's
// Robolectric-free precedent — needed for a real android.os.Parcel/Bundle round trip).
@RunWith(RobolectricTestRunner.class)
public class DownloadRequestTest {

    @Test
    public void parcelRoundTripPreservesParceledFieldsOnly() {
        Bundle arguments = new Bundle();
        arguments.putInt(DownloadRequest.REQUEST_ARG_PAGE_NR, 1);
        DownloadRequest original = new DownloadRequest("dest", "source", "title", 42L, 2,
                "lastModified", "user", "pass", true, arguments, true);
        // Progress fields are not parceled - set them non-zero to prove they are dropped, not
        // accidentally zero-by-coincidence.
        original.setProgressPercent(50);
        original.setSoFar(100L);
        original.setSize(200L);
        original.setStatusMsg(3);

        DownloadRequest restored = parcelRoundTrip(original);

        assertEquals(original.getDestination(), restored.getDestination());
        assertEquals(original.getSource(), restored.getSource());
        assertEquals(original.getTitle(), restored.getTitle());
        assertEquals(original.getFeedfileId(), restored.getFeedfileId());
        assertEquals(original.getFeedfileType(), restored.getFeedfileType());
        assertEquals(original.getLastModified(), restored.getLastModified());
        assertEquals(original.getUsername(), restored.getUsername());
        assertEquals(original.getPassword(), restored.getPassword());
        assertEquals(1, restored.getArguments().getInt(DownloadRequest.REQUEST_ARG_PAGE_NR));

        // Not parceled - the lossy-by-design subset. Restored copy has these at their zero
        // defaults, NOT the original's non-zero values.
        assertEquals(0, restored.getProgressPercent());
        assertEquals(0L, restored.getSoFar());
        assertEquals(0L, restored.getSize());

        // statusMsg has no public getter, but equals() does compare it - use that to prove it
        // did not survive the round trip: a same-shaped copy with statusMsg left at its 0
        // default equals the restored copy; a same-shaped copy with statusMsg matching the
        // original's non-zero value does not.
        DownloadRequest sameShapeZeroStatus = new DownloadRequest("dest", "source", "title", 42L, 2,
                "lastModified", "user", "pass", true, new Bundle(), true);
        assertEquals(sameShapeZeroStatus, restored);

        DownloadRequest sameShapeOriginalStatus = new DownloadRequest("dest", "source", "title", 42L, 2,
                "lastModified", "user", "pass", true, new Bundle(), true);
        sameShapeOriginalStatus.setStatusMsg(3);
        assertNotEquals(sameShapeOriginalStatus, restored);
    }

    @Test
    public void parcelNullAuthRoundTripsToNull() {
        DownloadRequest original = new DownloadRequest("dest", "source", "title", 1L, 2,
                null, null, null, false, new Bundle(), false);

        DownloadRequest restored = parcelRoundTrip(original);

        assertNull(restored.getUsername());
        assertNull(restored.getPassword());
    }

    @Test
    public void parcelEmptyAuthRoundTripsToNull() {
        DownloadRequest original = new DownloadRequest("dest", "source", "title", 1L, 2,
                null, "", "", false, new Bundle(), false);

        DownloadRequest restored = parcelRoundTrip(original);

        // Real, if benign, divergence: an originally-"" value round-trips to null, not "".
        assertNull(restored.getUsername());
        assertNull(restored.getPassword());
    }

    @Test
    public void equalsIgnoresArgumentsHashCodeIncludesArguments() {
        DownloadRequest a = new DownloadRequest("dest", "source", "title", 1L, 2,
                "lm", "user", "pass", false, new Bundle(), false);
        Bundle otherArguments = new Bundle();
        otherArguments.putInt(DownloadRequest.REQUEST_ARG_PAGE_NR, 5);
        DownloadRequest b = new DownloadRequest("dest", "source", "title", 1L, 2,
                "lm", "user", "pass", false, otherArguments, false);

        // equals() ignores arguments entirely.
        assertEquals(a, b);
        // hashCode() dereferences arguments.hashCode() unguarded, so two otherwise-identical
        // requests with different argument Bundle instances hash differently.
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void bundleParcelableArrayListPreservesAuth() {
        DownloadRequest noAuth = new DownloadRequest("dest1", "source1", "title1", 1L, 2,
                null, null, null, false, new Bundle(), false);
        DownloadRequest mixedAuth = new DownloadRequest("dest2", "source2", "title2", 2L, 2,
                null, null, "pass2", false, new Bundle(), false);

        ArrayList<DownloadRequest> toParcel = new ArrayList<>();
        toParcel.add(noAuth);
        toParcel.add(mixedAuth);

        Bundle bundleIn = new Bundle();
        bundleIn.putParcelableArrayList("requests", toParcel);

        Parcel parcel = Parcel.obtain();
        bundleIn.writeToParcel(parcel, 0);

        Bundle bundleOut = new Bundle();
        bundleOut.setClassLoader(DownloadRequest.class.getClassLoader());
        parcel.setDataPosition(0);
        bundleOut.readFromParcel(parcel);

        ArrayList<DownloadRequest> fromParcel = bundleOut.getParcelableArrayList("requests");

        assertEquals(2, fromParcel.size());
        assertNull(fromParcel.get(0).getUsername());
        assertNull(fromParcel.get(0).getPassword());
        assertNull(fromParcel.get(1).getUsername());
        assertEquals("pass2", fromParcel.get(1).getPassword());
        assertEquals("source2", fromParcel.get(1).getSource());
    }

    private static DownloadRequest parcelRoundTrip(DownloadRequest original) {
        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        return DownloadRequest.CREATOR.createFromParcel(parcel);
    }
}
