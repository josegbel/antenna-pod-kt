package de.danoeh.antennapod.net.sync.serviceinterface

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SynchronizationProviderTest {

    @Test
    fun fromIdentifierWithNullReturnsNull() {
        assertNull(SynchronizationProvider.fromIdentifier(null))
    }

    @Test
    fun fromIdentifierWithEmptyStringReturnsNull() {
        assertNull(SynchronizationProvider.fromIdentifier(""))
    }

    @Test
    fun fromIdentifierWithUnknownStringReturnsNull() {
        assertNull(SynchronizationProvider.fromIdentifier("NOT_A_PROVIDER"))
    }

    @Test
    fun fromIdentifierIsCaseSensitiveAndReturnsNullForLowerCase() {
        assertNull(SynchronizationProvider.fromIdentifier("gpodder_net"))
    }

    @Test
    fun fromIdentifierWithExactMatchReturnsGpodderNet() {
        assertEquals(SynchronizationProvider.GPODDER_NET, SynchronizationProvider.fromIdentifier("GPODDER_NET"))
    }

    @Test
    fun fromIdentifierWithExactMatchReturnsNextcloudGpodder() {
        assertEquals(
            SynchronizationProvider.NEXTCLOUD_GPODDER,
            SynchronizationProvider.fromIdentifier("NEXTCLOUD_GPODDER")
        )
    }

    @Test
    fun getIdentifierReturnsExactPersistedStringForGpodderNet() {
        assertEquals("GPODDER_NET", SynchronizationProvider.GPODDER_NET.identifier)
    }

    @Test
    fun getIdentifierReturnsExactPersistedStringForNextcloudGpodder() {
        assertEquals("NEXTCLOUD_GPODDER", SynchronizationProvider.NEXTCLOUD_GPODDER.identifier)
    }

    @Test
    fun valuesReturnsBothConstantsInDeclarationOrder() {
        assertArrayEquals(
            arrayOf(SynchronizationProvider.GPODDER_NET, SynchronizationProvider.NEXTCLOUD_GPODDER),
            SynchronizationProvider.values()
        )
    }
}
