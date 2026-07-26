package de.danoeh.antennapod.event;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

public class DownloadLogEventTest {

    @Test
    public void listUpdatedReturnsNewInstanceEachCall() {
        DownloadLogEvent first = DownloadLogEvent.listUpdated();
        DownloadLogEvent second = DownloadLogEvent.listUpdated();
        assertNotSame(first, second);
    }

    @Test
    public void toStringIsFixedString() {
        assertEquals("DownloadLogEvent", DownloadLogEvent.listUpdated().toString());
    }
}
