package de.danoeh.antennapod.net.sync.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction;
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings;

/**
 * Characterizes {@link SynchronizationQueueStorage} against the live Java implementation. This is
 * the on-disk pending-changes queue that holds a user's un-uploaded listening history, and nothing
 * in the repository tested it before this suite (176 LOC, zero tests). Runs under Robolectric
 * because SharedPreferences and org.json are Android-framework stubs under plain JUnit (disclosed,
 * scoped exception recorded in net/sync/service/build.gradle).
 */
@RunWith(RobolectricTestRunner.class)
public class SynchronizationQueueStorageTest {

    private static final String PREFS_NAME = "synchronization";
    private static final String QUEUED_EPISODE_ACTIONS = "sync_queued_episode_actions";
    private static final String QUEUED_FEEDS_REMOVED = "sync_removed";
    private static final String QUEUED_FEEDS_ADDED = "sync_added";

    private Context context;
    private SharedPreferences rawPrefs;
    private SynchronizationQueueStorage storage;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        SynchronizationSettings.init(context);
        rawPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        rawPrefs.edit().clear().commit();
        storage = new SynchronizationQueueStorage(context);
    }

    private void putRaw(String key, String json) {
        rawPrefs.edit().putString(key, json).commit();
    }

    private String getRaw(String key) {
        return rawPrefs.getString(key, "[]");
    }

    // ---- on-disk contract: file name, key names, empty defaults ----

    @Test
    public void storageUsesTheSynchronizationPrefsFile() {
        storage.enqueueFeedAdded("https://example.com/feed.xml");
        assertTrue(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(QUEUED_FEEDS_ADDED, "[]").contains("example.com"));
    }

    @Test
    public void getQueuedEpisodeActionsReturnsEmptyListWhenKeyAbsent() {
        assertTrue(storage.getQueuedEpisodeActions().isEmpty());
    }

    @Test
    public void getQueuedRemovedFeedsReturnsEmptyListWhenKeyAbsent() {
        assertTrue(storage.getQueuedRemovedFeeds().isEmpty());
    }

    @Test
    public void getQueuedAddedFeedsReturnsEmptyListWhenKeyAbsent() {
        assertTrue(storage.getQueuedAddedFeeds().isEmpty());
    }

    // ---- getQueuedEpisodeActions: parsing, the malformed-entry null, and partial reads ----

    @Test
    public void getQueuedEpisodeActionsParsesStoredActions() {
        String storedJson = "[{\"podcast\":\"podcast.a\",\"episode\":\"episode.1\",\"guid\":\"guid-1\","
                + "\"action\":\"play\",\"timestamp\":\"2021-01-01T08:00:00\","
                + "\"started\":5,\"position\":10,\"total\":20}]";
        putRaw(QUEUED_EPISODE_ACTIONS, storedJson);

        ArrayList<EpisodeAction> actions = storage.getQueuedEpisodeActions();

        assertEquals(1, actions.size());
        EpisodeAction action = actions.get(0);
        assertEquals("podcast.a", action.getPodcast());
        assertEquals("episode.1", action.getEpisode());
        assertEquals("guid-1", action.getGuid());
        assertEquals(EpisodeAction.PLAY, action.getAction());
        assertEquals(5, action.getStarted());
        assertEquals(10, action.getPosition());
        assertEquals(20, action.getTotal());
    }

    @Test
    public void getQueuedEpisodeActionsPutsNullIntoTheListForAMalformedEntry() {
        String storedJson = "[{\"podcast\":\"podcast.a\",\"episode\":\"episode.1\",\"action\":\"play\"},"
                + "{\"episode\":\"episode.2\",\"action\":\"play\"}]";
        putRaw(QUEUED_EPISODE_ACTIONS, storedJson);

        ArrayList<EpisodeAction> actions = storage.getQueuedEpisodeActions();

        assertEquals(2, actions.size());
        assertNull(actions.get(1));
    }

    @Test
    public void getQueuedEpisodeActionsReturnsPartialListWhenAnEntryIsNotAnObject() {
        String storedJson = "[{\"podcast\":\"podcast.a\",\"episode\":\"episode.1\",\"action\":\"play\"},"
                + "\"not-an-object\"]";
        putRaw(QUEUED_EPISODE_ACTIONS, storedJson);

        ArrayList<EpisodeAction> actions = storage.getQueuedEpisodeActions();

        assertEquals(1, actions.size());
        assertEquals("podcast.a", actions.get(0).getPodcast());
    }

    @Test
    public void getQueuedRemovedFeedsCoercesANonStringEntryToItsStringRepresentation() {
        putRaw(QUEUED_FEEDS_REMOVED, "[\"https://a.example/feed.xml\",5]");

        List<String> feeds = storage.getQueuedRemovedFeeds();

        assertEquals(Arrays.asList("https://a.example/feed.xml", "5"), feeds);
    }

    @Test
    public void getQueuedAddedFeedsCoercesANonStringEntryToItsStringRepresentation() {
        putRaw(QUEUED_FEEDS_ADDED, "[\"https://a.example/feed.xml\",5]");

        List<String> feeds = storage.getQueuedAddedFeeds();

        assertEquals(Arrays.asList("https://a.example/feed.xml", "5"), feeds);
    }

    @Test
    public void getQueuedRemovedFeedsReturnsEmptyListWhenStoredJsonIsMalformed() {
        putRaw(QUEUED_FEEDS_REMOVED, "[\"https://a.example/feed.xml\"");

        assertTrue(storage.getQueuedRemovedFeeds().isEmpty());
    }

    @Test
    public void getQueuedAddedFeedsReturnsEmptyListWhenStoredJsonIsMalformed() {
        putRaw(QUEUED_FEEDS_ADDED, "[\"https://a.example/feed.xml\"");

        assertTrue(storage.getQueuedAddedFeeds().isEmpty());
    }

    // ---- clearing ----

    @Test
    public void clearEpisodeActionQueueClearsOnlyItsOwnKey() {
        storage.enqueueEpisodeAction(playAction("podcast.a", "episode.1"));
        storage.enqueueFeedAdded("https://a.example/feed.xml");

        storage.clearEpisodeActionQueue();

        assertEquals("[]", getRaw(QUEUED_EPISODE_ACTIONS));
        assertTrue(getRaw(QUEUED_FEEDS_ADDED).contains("a.example"));
    }

    @Test
    public void clearFeedQueuesClearsOnlyItsOwnKeys() {
        storage.enqueueEpisodeAction(playAction("podcast.a", "episode.1"));
        storage.enqueueFeedAdded("https://a.example/feed.xml");
        storage.enqueueFeedRemoved("https://b.example/feed.xml");

        storage.clearFeedQueues();

        assertEquals("[]", getRaw(QUEUED_FEEDS_ADDED));
        assertEquals("[]", getRaw(QUEUED_FEEDS_REMOVED));
        assertTrue(getRaw(QUEUED_EPISODE_ACTIONS).contains("podcast.a"));
    }

    @Test
    public void clearQueueClearsAllThreeKeys() {
        storage.enqueueEpisodeAction(playAction("podcast.a", "episode.1"));
        storage.enqueueFeedAdded("https://a.example/feed.xml");
        storage.enqueueFeedRemoved("https://b.example/feed.xml");

        storage.clearQueue();

        assertEquals("[]", getRaw(QUEUED_EPISODE_ACTIONS));
        assertEquals("[]", getRaw(QUEUED_FEEDS_ADDED));
        assertEquals("[]", getRaw(QUEUED_FEEDS_REMOVED));
    }

    @Test
    public void clearQueueAlsoResetsSynchronizationTimestamps() {
        SynchronizationSettings.setLastSubscriptionSynchronizationAttemptTimestamp(123456789L);

        storage.clearQueue();

        assertEquals(0L, SynchronizationSettings.getLastSubscriptionSynchronizationTimestamp());
    }

    // ---- enqueueFeedAdded / enqueueFeedRemoved: cross-queue removal ----

    @Test
    public void enqueueFeedAddedAppendsToAddedQueueAndRemovesFromRemovedQueue() {
        storage.enqueueFeedRemoved("https://a.example/feed.xml");

        storage.enqueueFeedAdded("https://a.example/feed.xml");

        assertEquals(Collections.singletonList("https://a.example/feed.xml"), storage.getQueuedAddedFeeds());
        assertTrue(storage.getQueuedRemovedFeeds().isEmpty());
    }

    @Test
    public void enqueueFeedAddedLeavesTheOppositeQueueUnchangedWhenUrlNotPresent() {
        storage.enqueueFeedRemoved("https://other.example/feed.xml");

        storage.enqueueFeedAdded("https://a.example/feed.xml");

        assertEquals(Collections.singletonList("https://other.example/feed.xml"), storage.getQueuedRemovedFeeds());
    }

    @Test
    public void enqueueFeedRemovedAppendsToRemovedQueueAndRemovesFromAddedQueue() {
        storage.enqueueFeedAdded("https://a.example/feed.xml");

        storage.enqueueFeedRemoved("https://a.example/feed.xml");

        assertEquals(Collections.singletonList("https://a.example/feed.xml"), storage.getQueuedRemovedFeeds());
        assertTrue(storage.getQueuedAddedFeeds().isEmpty());
    }

    @Test
    public void enqueueFeedRemovedLeavesTheOppositeQueueUnchangedWhenUrlNotPresent() {
        storage.enqueueFeedAdded("https://other.example/feed.xml");

        storage.enqueueFeedRemoved("https://a.example/feed.xml");

        assertEquals(Collections.singletonList("https://other.example/feed.xml"), storage.getQueuedAddedFeeds());
    }

    @Test
    public void enqueueFeedAddedAcceptsNullUrl() {
        storage.enqueueFeedAdded(null);

        assertTrue(getRaw(QUEUED_FEEDS_ADDED).contains("null"));
    }

    @Test
    public void enqueueFeedRemovedAcceptsNullUrl() {
        storage.enqueueFeedRemoved(null);

        assertTrue(getRaw(QUEUED_FEEDS_REMOVED).contains("null"));
    }

    // ---- enqueueEpisodeAction ----

    @Test
    public void enqueueEpisodeActionAppendsWrittenJson() throws JSONException {
        EpisodeAction action = playAction("podcast.a", "episode.1");

        storage.enqueueEpisodeAction(action);

        JSONArray expected = new JSONArray();
        expected.put(action.writeToJsonObject());
        assertEquals(expected.toString(), getRaw(QUEUED_EPISODE_ACTIONS));
    }

    @Test
    public void enqueueEpisodeActionWithNullActionThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> storage.enqueueEpisodeAction(null));
    }

    // ---- removeLegacyConflictingFeedEntries: the live List.toString()-into-JSON defect (D9) ----

    @Test
    public void removeLegacyConflictingFeedEntriesWritesUnquotedListToStringIntoBothKeys() {
        putRaw(QUEUED_FEEDS_REMOVED, jsonArrayOf("https://a.example/feed.xml", "https://b.example/feed.xml"));
        putRaw(QUEUED_FEEDS_ADDED, jsonArrayOf());

        storage.removeLegacyConflictingFeedEntries(Collections.emptyList());

        assertEquals("[https://a.example/feed.xml, https://b.example/feed.xml]", getRaw(QUEUED_FEEDS_REMOVED));
    }

    @Test
    public void removeLegacyConflictingFeedEntriesLeavesBothQueuesUnreadableForRealFeedUrls() {
        putRaw(QUEUED_FEEDS_REMOVED, jsonArrayOf("https://a.example/feed.xml", "https://b.example/feed.xml"));
        putRaw(QUEUED_FEEDS_ADDED, jsonArrayOf("https://c.example/feed.xml"));

        storage.removeLegacyConflictingFeedEntries(Collections.emptyList());

        assertTrue(storage.getQueuedRemovedFeeds().isEmpty());
        assertTrue(storage.getQueuedAddedFeeds().isEmpty());
    }

    @Test
    public void removeLegacyConflictingFeedEntriesRoundTripsWhenValuesAreJsonTokenerSafe() {
        putRaw(QUEUED_FEEDS_REMOVED, jsonArrayOf("a", "b"));
        putRaw(QUEUED_FEEDS_ADDED, jsonArrayOf());

        storage.removeLegacyConflictingFeedEntries(Collections.emptyList());

        assertEquals(Arrays.asList("a", "b"), storage.getQueuedRemovedFeeds());
    }

    private static String jsonArrayOf(String... values) {
        JSONArray array = new JSONArray();
        for (String value : values) {
            array.put(value);
        }
        return array.toString();
    }

    private static EpisodeAction playAction(String podcast, String episode) {
        return new EpisodeAction.Builder(podcast, episode, EpisodeAction.PLAY)
                .currentTimestamp()
                .started(1)
                .position(2)
                .total(3)
                .build();
    }
}
