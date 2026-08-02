package de.danoeh.antennapod.net.sync.serviceinterface;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

/**
 * Characterizes {@link SubscriptionChanges} against the live Java implementation, including its
 * exact (and misleadingly-named, singular "Change") {@code toString()} format, which reaches
 * {@code Log.d} in production.
 */
public class SubscriptionChangesTest {

    @Test
    public void constructorRoundTripsAddedRemovedAndTimestamp() {
        List<String> added = Arrays.asList("feed-1", "feed-2");
        List<String> removed = Collections.singletonList("feed-3");

        SubscriptionChanges changes = new SubscriptionChanges(added, removed, 123L);

        assertEquals(added, changes.getAdded());
        assertEquals(removed, changes.getRemoved());
        assertEquals(123L, changes.getTimestamp());
    }

    @Test
    public void toStringMatchesExactGoldenFormat() {
        List<String> added = Collections.singletonList("feed-1");
        List<String> removed = Collections.singletonList("feed-2");
        SubscriptionChanges changes = new SubscriptionChanges(added, removed, 42L);

        String expected = "SubscriptionChange [added=" + added + ", removed=" + removed + ", timestamp=42]";
        assertEquals(expected, changes.toString());
    }
}
