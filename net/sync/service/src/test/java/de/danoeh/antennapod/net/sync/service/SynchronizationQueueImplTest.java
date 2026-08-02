package de.danoeh.antennapod.net.sync.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Date;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction;
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings;
import de.danoeh.antennapod.storage.preferences.UserPreferences;

/**
 * Characterizes {@link SynchronizationQueueImpl}'s guard conditions against the live Java
 * implementation. Milestone 12 declines androidx.work:work-testing (D12), so the accepted-case
 * tests below invoke through a helper that tolerates the IllegalStateException WorkManager throws
 * when uninitialized under Robolectric -- the queued content is written to SharedPreferences
 * before sync() is reached, so it is observable and identical either way.
 */
@RunWith(RobolectricTestRunner.class)
public class SynchronizationQueueImplTest {

    private static final String PREFS_NAME = "synchronization";
    private static final String QUEUED_EPISODE_ACTIONS = "sync_queued_episode_actions";
    private static final String QUEUED_FEEDS_ADDED = "sync_added";
    private static final String QUEUED_FEEDS_REMOVED = "sync_removed";

    private Context context;
    private SharedPreferences rawPrefs;
    private SynchronizationQueueImpl queue;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        SynchronizationSettings.init(context);
        UserPreferences.init(context);
        rawPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        rawPrefs.edit().clear().commit();
        queue = new SynchronizationQueueImpl(context);
    }

    private String getRaw(String key) {
        return rawPrefs.getString(key, "[]");
    }

    private void connectProvider() {
        SynchronizationSettings.setSelectedSyncProvider("GPODDER_NET");
    }

    private FeedMedia eligibleMedia() {
        Feed feed = new Feed("https://example.com/feed.xml", null, null);
        FeedItem item = new FeedItem(0, "title", "guid", "link", new Date(), 0, feed);
        FeedMedia media = new FeedMedia(item, "https://example.com/episode.mp3", 2, "audio/mpeg");
        item.setMedia(media);
        return media;
    }

    private void invokeAllowingUninitializedWorkManager(Runnable call) {
        try {
            call.run();
        } catch (IllegalStateException ignoredWorkManagerNotInitialized) {
            // Expected under Robolectric without androidx.work:work-testing (D11/D12); the
            // storage write already happened before sync() was reached.
        }
    }

    private void assertQueuedEpisodeActionCount(int expected) throws JSONException {
        JSONArray stored = new JSONArray(getRaw(QUEUED_EPISODE_ACTIONS));
        assertEquals(expected, stored.length());
    }

    // ---- provider-not-connected guard ----

    @Test
    public void enqueueFeedAddedDoesNothingWhenProviderNotConnected() {
        queue.enqueueFeedAdded("https://example.com/feed.xml");
        assertEquals("[]", getRaw(QUEUED_FEEDS_ADDED));
    }

    @Test
    public void enqueueFeedRemovedDoesNothingWhenProviderNotConnected() {
        queue.enqueueFeedRemoved("https://example.com/feed.xml");
        assertEquals("[]", getRaw(QUEUED_FEEDS_REMOVED));
    }

    @Test
    public void enqueueEpisodeActionDoesNothingWhenProviderNotConnected() {
        EpisodeAction action = new EpisodeAction.Builder("p", "e", EpisodeAction.PLAY)
                .currentTimestamp().position(10).build();
        queue.enqueueEpisodeAction(action);
        assertEquals("[]", getRaw(QUEUED_EPISODE_ACTIONS));
    }

    @Test
    public void enqueueEpisodePlayedDoesNothingWhenProviderNotConnected() {
        queue.enqueueEpisodePlayed(null, true);
        assertEquals("[]", getRaw(QUEUED_EPISODE_ACTIONS));
    }

    // ---- enqueueEpisodePlayed: the forced-nullable media parameter ----

    @Test
    public void enqueueEpisodePlayedWithNullMediaThrowsNullPointerExceptionWhenProviderConnected() {
        connectProvider();
        assertThrows(NullPointerException.class, () -> queue.enqueueEpisodePlayed(null, true));
    }

    @Test
    public void enqueueEpisodePlayedWithNullFeedThrowsNullPointerException() {
        connectProvider();
        FeedItem item = new FeedItem(0, "title", "guid", "link", new Date(), 0, null);
        FeedMedia media = new FeedMedia(item, "https://example.com/episode.mp3", 2, "audio/mpeg");
        assertThrows(NullPointerException.class, () -> queue.enqueueEpisodePlayed(media, true));
    }

    // ---- the eight enqueueEpisodePlayed guard tests (AC19) ----

    @Test
    public void enqueueEpisodePlayedDoesNothingWhenItemIsNull() {
        connectProvider();
        FeedMedia media = new FeedMedia(null, "https://example.com/episode.mp3", 2, "audio/mpeg");
        queue.enqueueEpisodePlayed(media, true);
        assertEquals("[]", getRaw(QUEUED_EPISODE_ACTIONS));
    }

    @Test
    public void enqueueEpisodePlayedDoesNothingForALocalFeed() {
        connectProvider();
        Feed feed = new Feed(Feed.PREFIX_LOCAL_FOLDER + "folder", null, null);
        FeedItem item = new FeedItem(0, "title", "guid", "link", new Date(), 0, feed);
        FeedMedia media = new FeedMedia(item, "https://example.com/episode.mp3", 2, "audio/mpeg");
        queue.enqueueEpisodePlayed(media, true);
        assertEquals("[]", getRaw(QUEUED_EPISODE_ACTIONS));
    }

    @Test
    public void enqueueEpisodePlayedDoesNothingForANotSubscribedFeed() {
        connectProvider();
        FeedMedia media = eligibleMedia();
        media.getItem().getFeed().setState(Feed.STATE_NOT_SUBSCRIBED);
        queue.enqueueEpisodePlayed(media, true);
        assertEquals("[]", getRaw(QUEUED_EPISODE_ACTIONS));
    }

    @Test
    public void enqueueEpisodePlayedRejectsNegativeStartPosition() {
        connectProvider();
        FeedMedia media = eligibleMedia();
        media.setPosition(5000);
        queue.enqueueEpisodePlayed(media, true);
        assertEquals("[]", getRaw(QUEUED_EPISODE_ACTIONS));
    }

    @Test
    public void enqueueEpisodePlayedAcceptsStartPositionZero() throws JSONException {
        connectProvider();
        FeedMedia media = eligibleMedia();
        media.setPosition(0);
        media.onPlaybackStart();

        invokeAllowingUninitializedWorkManager(() -> queue.enqueueEpisodePlayed(media, true));

        assertQueuedEpisodeActionCount(1);
    }

    @Test
    public void enqueueEpisodePlayedNotCompletedRejectsWhenStartPositionEqualsPosition() {
        connectProvider();
        FeedMedia media = eligibleMedia();
        media.setPosition(5000);
        media.onPlaybackStart();

        queue.enqueueEpisodePlayed(media, false);

        assertEquals("[]", getRaw(QUEUED_EPISODE_ACTIONS));
    }

    @Test
    public void enqueueEpisodePlayedNotCompletedAcceptsWhenStartPositionIsOneLessThanPosition() throws JSONException {
        connectProvider();
        FeedMedia media = eligibleMedia();
        media.setPosition(4999);
        media.onPlaybackStart();
        media.setPosition(5000);

        invokeAllowingUninitializedWorkManager(() -> queue.enqueueEpisodePlayed(media, false));

        assertQueuedEpisodeActionCount(1);
    }

    @Test
    public void enqueueEpisodePlayedCompletedAcceptsWhenStartPositionEqualsPosition() throws JSONException {
        connectProvider();
        FeedMedia media = eligibleMedia();
        media.setPosition(5000);
        media.onPlaybackStart();

        invokeAllowingUninitializedWorkManager(() -> queue.enqueueEpisodePlayed(media, true));

        assertQueuedEpisodeActionCount(1);
    }

    // ---- arithmetic: millisecond -> second conversion and completed-vs-position selection ----

    @Test
    public void enqueueEpisodePlayedWritesSecondsNotMillisecondsForACompletedEpisode() throws JSONException {
        connectProvider();
        FeedMedia media = eligibleMedia();
        media.setDuration(120000);
        media.setPosition(60000);
        media.onPlaybackStart();

        invokeAllowingUninitializedWorkManager(() -> queue.enqueueEpisodePlayed(media, true));

        JSONObject action = new JSONArray(getRaw(QUEUED_EPISODE_ACTIONS)).getJSONObject(0);
        assertEquals(60, action.getInt("started"));
        assertEquals(120, action.getInt("position"));
        assertEquals(120, action.getInt("total"));
    }

    @Test
    public void enqueueEpisodePlayedUsesPositionRatherThanDurationWhenNotCompleted() throws JSONException {
        connectProvider();
        FeedMedia media = eligibleMedia();
        media.setDuration(120000);
        media.setPosition(30000);
        media.onPlaybackStart();
        media.setPosition(45000);

        invokeAllowingUninitializedWorkManager(() -> queue.enqueueEpisodePlayed(media, false));

        JSONObject action = new JSONArray(getRaw(QUEUED_EPISODE_ACTIONS)).getJSONObject(0);
        assertEquals(30, action.getInt("started"));
        assertEquals(45, action.getInt("position"));
        assertEquals(120, action.getInt("total"));
    }
}
