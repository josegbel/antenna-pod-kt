package de.danoeh.antennapod.net.sync.serviceinterface;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.List;

import org.junit.Test;

/**
 * Characterizes {@link EpisodeActionChanges} against the live Java implementation, including its
 * exact (and misleadingly-named — it does not match the class name) {@code toString()} format,
 * which reaches {@code Log.d} in production.
 */
public class EpisodeActionChangesTest {

    @Test
    public void constructorRoundTripsEpisodeActionsAndTimestamp() {
        EpisodeAction action = new EpisodeAction.Builder("p", "e", EpisodeAction.Action.PLAY).build();
        List<EpisodeAction> actions = Collections.singletonList(action);

        EpisodeActionChanges changes = new EpisodeActionChanges(actions, 55L);

        assertEquals(actions, changes.getEpisodeActions());
        assertEquals(55L, changes.getTimestamp());
    }

    @Test
    public void toStringMatchesExactGoldenFormat() {
        EpisodeAction action = new EpisodeAction.Builder("p", "e", EpisodeAction.Action.PLAY).build();
        List<EpisodeAction> actions = Collections.singletonList(action);
        EpisodeActionChanges changes = new EpisodeActionChanges(actions, 77L);

        String expected = "EpisodeActionGetResponse{episodeActions=" + actions + ", timestamp=77}";
        assertEquals(expected, changes.toString());
    }
}
