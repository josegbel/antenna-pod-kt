package de.danoeh.antennapod.model.download

import android.os.Bundle
import android.os.Parcel
import java.util.ArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Characterizes DownloadRequest's Parcelable behavior against the live Java implementation,
// under Robolectric (this milestone's disclosed one-milestone exception to :model's
// Robolectric-free precedent — needed for a real android.os.Parcel/Bundle round trip).
@RunWith(RobolectricTestRunner::class)
class DownloadRequestTest {

    @Test
    fun parcelRoundTripPreservesParceledFieldsOnly() {
        val arguments = Bundle()
        arguments.putInt(DownloadRequest.REQUEST_ARG_PAGE_NR, 1)
        val original = DownloadRequest(
            "dest", "source", "title", 42L, 2,
            "lastModified", "user", "pass", true, arguments, true
        )
        // Progress fields are not parceled - set them non-zero to prove they are dropped, not
        // accidentally zero-by-coincidence.
        original.progressPercent = 50
        original.soFar = 100L
        original.size = 200L
        original.setStatusMsg(3)

        val restored = parcelRoundTrip(original)

        assertEquals(original.destination, restored.destination)
        assertEquals(original.source, restored.source)
        assertEquals(original.title, restored.title)
        assertEquals(original.feedfileId, restored.feedfileId)
        assertEquals(original.feedfileType, restored.feedfileType)
        assertEquals(original.lastModified, restored.lastModified)
        assertEquals(original.username, restored.username)
        assertEquals(original.password, restored.password)
        assertEquals(1, restored.arguments.getInt(DownloadRequest.REQUEST_ARG_PAGE_NR))

        // Not parceled - the lossy-by-design subset. Restored copy has these at their zero
        // defaults, NOT the original's non-zero values.
        assertEquals(0, restored.progressPercent)
        assertEquals(0L, restored.soFar)
        assertEquals(0L, restored.size)

        // statusMsg has no public getter, but equals() does compare it - use that to prove it
        // did not survive the round trip: a same-shaped copy with statusMsg left at its 0
        // default equals the restored copy; a same-shaped copy with statusMsg matching the
        // original's non-zero value does not.
        val sameShapeZeroStatus = DownloadRequest(
            "dest", "source", "title", 42L, 2,
            "lastModified", "user", "pass", true, Bundle(), true
        )
        assertEquals(sameShapeZeroStatus, restored)

        val sameShapeOriginalStatus = DownloadRequest(
            "dest", "source", "title", 42L, 2,
            "lastModified", "user", "pass", true, Bundle(), true
        )
        sameShapeOriginalStatus.setStatusMsg(3)
        assertNotEquals(sameShapeOriginalStatus, restored)
    }

    @Test
    fun parcelNullAuthRoundTripsToNull() {
        val original = DownloadRequest(
            "dest", "source", "title", 1L, 2,
            null, null, null, false, Bundle(), false
        )

        val restored = parcelRoundTrip(original)

        assertNull(restored.username)
        assertNull(restored.password)
    }

    @Test
    fun parcelEmptyAuthRoundTripsToNull() {
        val original = DownloadRequest(
            "dest", "source", "title", 1L, 2,
            null, "", "", false, Bundle(), false
        )

        val restored = parcelRoundTrip(original)

        // Real, if benign, divergence: an originally-"" value round-trips to null, not "".
        assertNull(restored.username)
        assertNull(restored.password)
    }

    @Test
    fun equalsIgnoresArgumentsHashCodeIncludesArguments() {
        val a = DownloadRequest(
            "dest", "source", "title", 1L, 2,
            "lm", "user", "pass", false, Bundle(), false
        )
        val otherArguments = Bundle()
        otherArguments.putInt(DownloadRequest.REQUEST_ARG_PAGE_NR, 5)
        val b = DownloadRequest(
            "dest", "source", "title", 1L, 2,
            "lm", "user", "pass", false, otherArguments, false
        )

        // equals() ignores arguments entirely.
        assertEquals(a, b)
        // hashCode() dereferences arguments.hashCode() unguarded, so two otherwise-identical
        // requests with different argument Bundle instances hash differently.
        assertNotEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun bundleParcelableArrayListPreservesAuth() {
        val noAuth = DownloadRequest(
            "dest1", "source1", "title1", 1L, 2,
            null, null, null, false, Bundle(), false
        )
        val mixedAuth = DownloadRequest(
            "dest2", "source2", "title2", 2L, 2,
            null, null, "pass2", false, Bundle(), false
        )

        val toParcel = ArrayList<DownloadRequest>()
        toParcel.add(noAuth)
        toParcel.add(mixedAuth)

        val bundleIn = Bundle()
        bundleIn.putParcelableArrayList("requests", toParcel)

        val parcel = Parcel.obtain()
        bundleIn.writeToParcel(parcel, 0)

        val bundleOut = Bundle()
        bundleOut.classLoader = DownloadRequest::class.java.classLoader
        parcel.setDataPosition(0)
        bundleOut.readFromParcel(parcel)

        // Bundle.getParcelableArrayList's Kotlin signature returns a nullable ArrayList; the
        // Bundle was just populated with putParcelableArrayList above, so it is always present.
        val fromParcel = bundleOut.getParcelableArrayList<DownloadRequest>("requests")!!

        assertEquals(2, fromParcel.size)
        assertNull(fromParcel.get(0).username)
        assertNull(fromParcel.get(0).password)
        assertNull(fromParcel.get(1).username)
        assertEquals("pass2", fromParcel.get(1).password)
        assertEquals("source2", fromParcel.get(1).source)
    }

    private fun parcelRoundTrip(original: DownloadRequest): DownloadRequest {
        val parcel = Parcel.obtain()
        original.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        return DownloadRequest.CREATOR.createFromParcel(parcel)
    }
}
