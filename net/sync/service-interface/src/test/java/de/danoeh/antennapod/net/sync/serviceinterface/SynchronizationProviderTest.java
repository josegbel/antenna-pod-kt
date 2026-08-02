package de.danoeh.antennapod.net.sync.serviceinterface;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class SynchronizationProviderTest {

    @Test
    public void fromIdentifierWithNullReturnsNull() {
        assertNull(SynchronizationProvider.fromIdentifier(null));
    }

    @Test
    public void fromIdentifierWithEmptyStringReturnsNull() {
        assertNull(SynchronizationProvider.fromIdentifier(""));
    }

    @Test
    public void fromIdentifierWithUnknownStringReturnsNull() {
        assertNull(SynchronizationProvider.fromIdentifier("NOT_A_PROVIDER"));
    }

    @Test
    public void fromIdentifierIsCaseSensitiveAndReturnsNullForLowerCase() {
        assertNull(SynchronizationProvider.fromIdentifier("gpodder_net"));
    }

    @Test
    public void fromIdentifierWithExactMatchReturnsGpodderNet() {
        assertEquals(SynchronizationProvider.GPODDER_NET, SynchronizationProvider.fromIdentifier("GPODDER_NET"));
    }

    @Test
    public void fromIdentifierWithExactMatchReturnsNextcloudGpodder() {
        assertEquals(SynchronizationProvider.NEXTCLOUD_GPODDER,
                SynchronizationProvider.fromIdentifier("NEXTCLOUD_GPODDER"));
    }

    @Test
    public void getIdentifierReturnsExactPersistedStringForGpodderNet() {
        assertEquals("GPODDER_NET", SynchronizationProvider.GPODDER_NET.getIdentifier());
    }

    @Test
    public void getIdentifierReturnsExactPersistedStringForNextcloudGpodder() {
        assertEquals("NEXTCLOUD_GPODDER", SynchronizationProvider.NEXTCLOUD_GPODDER.getIdentifier());
    }

    @Test
    public void valuesReturnsBothConstantsInDeclarationOrder() {
        assertArrayEquals(
                new SynchronizationProvider[] {
                    SynchronizationProvider.GPODDER_NET, SynchronizationProvider.NEXTCLOUD_GPODDER
                },
                SynchronizationProvider.values());
    }
}
