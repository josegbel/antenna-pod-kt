package de.danoeh.antennapod.net.sync.service;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import androidx.core.util.Pair;

import org.junit.Test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction;

/**
 * Characterizes {@link EpisodeActionFilter#getRemoteActionsOverridingLocalActions(List, List)}'s
 * untested branches against the live Java implementation. {@link EpisodeActionFilterTest} only
 * ever exercises the PLAY case and asserts map size; this suite adds the NEW/DOWNLOAD/DELETE
 * no-ops, the nullable-enum switch (D6), the nullable-list-element consequence of Milestone 12's
 * D3, the remote-vs-remote dedupe, and the local-null-timestamp replacement.
 */
public class EpisodeActionFilterCharacterizationTest {

    private static Date parse(String isoDateTime) throws ParseException {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(isoDateTime);
    }

    private static EpisodeAction playAction(String podcast, String episode, Date timestamp)
            throws ParseException {
        return new EpisodeAction.Builder(podcast, episode, EpisodeAction.PLAY)
                .timestamp(timestamp)
                .position(10)
                .build();
    }

    @Test
    public void newActionIsANoOp() {
        EpisodeAction remote = new EpisodeAction.Builder("p", "e", EpisodeAction.NEW).currentTimestamp().build();

        Map<Pair<String, String>, EpisodeAction> result = EpisodeActionFilter
                .getRemoteActionsOverridingLocalActions(Collections.singletonList(remote), Collections.emptyList());

        assertTrue(result.isEmpty());
    }

    @Test
    public void downloadActionIsANoOp() {
        EpisodeAction remote = new EpisodeAction.Builder("p", "e", EpisodeAction.DOWNLOAD)
                .currentTimestamp().build();

        Map<Pair<String, String>, EpisodeAction> result = EpisodeActionFilter
                .getRemoteActionsOverridingLocalActions(Collections.singletonList(remote), Collections.emptyList());

        assertTrue(result.isEmpty());
    }

    @Test
    public void deleteActionIsANoOp() {
        EpisodeAction remote = new EpisodeAction.Builder("p", "e", EpisodeAction.DELETE)
                .currentTimestamp().build();

        Map<Pair<String, String>, EpisodeAction> result = EpisodeActionFilter
                .getRemoteActionsOverridingLocalActions(Collections.singletonList(remote), Collections.emptyList());

        assertTrue(result.isEmpty());
    }

    @Test
    public void nullActionThrowsNullPointerException() {
        EpisodeAction remote = new EpisodeAction.Builder("p", "e", null).currentTimestamp().build();

        assertThrows(NullPointerException.class, () -> EpisodeActionFilter
                .getRemoteActionsOverridingLocalActions(Collections.singletonList(remote), Collections.emptyList()));
    }

    @Test
    public void queuedLocalActionThatIsNullThrowsNullPointerException() {
        List<EpisodeAction> queued = new ArrayList<>();
        queued.add(null);

        assertThrows(NullPointerException.class, () -> EpisodeActionFilter
                .getRemoteActionsOverridingLocalActions(Collections.emptyList(), queued));
    }

    @Test
    public void secondRemoteActionForSameKeyDoesNotOverrideAnEarlierOneWhenOlder() throws ParseException {
        EpisodeAction remoteLate = playAction("p", "e", parse("2021-01-01 09:00:00"));
        EpisodeAction remoteEarly = playAction("p", "e", parse("2021-01-01 08:00:00"));
        List<EpisodeAction> remotes = new ArrayList<>();
        remotes.add(remoteLate);
        remotes.add(remoteEarly);

        Map<Pair<String, String>, EpisodeAction> result = EpisodeActionFilter
                .getRemoteActionsOverridingLocalActions(remotes, Collections.emptyList());

        assertSame(remoteLate, result.get(new Pair<>("p", "e")));
    }

    @Test
    public void secondRemoteActionForSameKeyOverridesAnEarlierOneWhenNewer() throws ParseException {
        EpisodeAction remoteEarly = playAction("p", "e", parse("2021-01-01 08:00:00"));
        EpisodeAction remoteLate = playAction("p", "e", parse("2021-01-01 09:00:00"));
        List<EpisodeAction> remotes = new ArrayList<>();
        remotes.add(remoteEarly);
        remotes.add(remoteLate);

        Map<Pair<String, String>, EpisodeAction> result = EpisodeActionFilter
                .getRemoteActionsOverridingLocalActions(remotes, Collections.emptyList());

        assertSame(remoteLate, result.get(new Pair<>("p", "e")));
    }

    @Test
    public void localActionWithNullTimestampIsReplacedByALaterLocalAction() throws ParseException {
        EpisodeAction localNoTimestamp = new EpisodeAction.Builder("p", "e", EpisodeAction.PLAY)
                .position(1).build();
        EpisodeAction localWithTimestamp = playAction("p", "e", parse("2021-01-01 09:00:00"));
        List<EpisodeAction> queued = new ArrayList<>();
        queued.add(localNoTimestamp);
        queued.add(localWithTimestamp);

        EpisodeAction remote = playAction("p", "e", parse("2021-01-01 08:00:00"));

        Map<Pair<String, String>, EpisodeAction> result = EpisodeActionFilter
                .getRemoteActionsOverridingLocalActions(Collections.singletonList(remote), queued);

        assertTrue(result.isEmpty());
    }

    @Test
    public void returnedMapValueIsTheRemoteActionInstance() {
        EpisodeAction remote = new EpisodeAction.Builder("p", "e", EpisodeAction.PLAY)
                .currentTimestamp().position(10).build();

        Map<Pair<String, String>, EpisodeAction> result = EpisodeActionFilter
                .getRemoteActionsOverridingLocalActions(Collections.singletonList(remote), Collections.emptyList());

        assertSame(remote, result.get(new Pair<>("p", "e")));
    }

    @Test
    public void keyUsesAndroidxCoreUtilPairEqualsSemantics() {
        EpisodeAction remote = new EpisodeAction.Builder("p", "e", EpisodeAction.PLAY)
                .currentTimestamp().position(10).build();

        Map<Pair<String, String>, EpisodeAction> result = EpisodeActionFilter
                .getRemoteActionsOverridingLocalActions(Collections.singletonList(remote), Collections.emptyList());

        assertTrue(result.containsKey(new Pair<>("p", "e")));
    }
}
