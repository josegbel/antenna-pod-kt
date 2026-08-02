package de.danoeh.antennapod.net.sync.serviceinterface;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Characterizes {@link EpisodeAction#readFromJsonObject(JSONObject)} and
 * {@link EpisodeAction#writeToJsonObject()} against the live Java implementation. These two
 * methods define both the gpodder.net/Nextcloud wire format and the on-disk SharedPreferences
 * format of the user's pending-sync queue, and nothing in the repository tested them before this
 * suite. Runs under Robolectric because org.json.JSONObject is an Android-framework stub under
 * plain JUnit.
 */
@RunWith(RobolectricTestRunner.class)
public class EpisodeActionJsonTest {

    private static final String PODCAST = "https://example.com/feed.xml";
    private static final String EPISODE = "https://example.com/episode.mp3";

    private static JSONObject baseJson(String action) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("podcast", PODCAST);
        obj.put("episode", EPISODE);
        obj.put("action", action);
        return obj;
    }

    private static long epochMillisUtc(String isoLocalDateTime) {
        return LocalDateTime.parse(isoLocalDateTime).toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    private static Date dateAtUtc(int year, int month, int day, int hour, int minute, int second) {
        return Date.from(LocalDateTime.of(year, month, day, hour, minute, second).toInstant(ZoneOffset.UTC));
    }

    private static Date dateAtUtcWithNanos(int year, int month, int day, int hour, int minute, int second,
            int nanoOfSecond) {
        return Date.from(
                LocalDateTime.of(year, month, day, hour, minute, second, nanoOfSecond).toInstant(ZoneOffset.UTC));
    }

    // ---- read side (18) ----

    @Test
    public void readFromJsonObjectParsesFullPlayAction() throws JSONException {
        JSONObject json = baseJson("play");
        json.put("timestamp", "2021-01-02T03:04:05");
        json.put("guid", "guid-1");
        json.put("started", 10);
        json.put("position", 20);
        json.put("total", 300);

        EpisodeAction action = EpisodeAction.readFromJsonObject(json);

        assertEquals(PODCAST, action.getPodcast());
        assertEquals(EPISODE, action.getEpisode());
        assertEquals(EpisodeAction.Action.PLAY, action.getAction());
        assertEquals("guid-1", action.getGuid());
        assertEquals(epochMillisUtc("2021-01-02T03:04:05"), action.getTimestamp().getTime());
        assertEquals(10, action.getStarted());
        assertEquals(20, action.getPosition());
        assertEquals(300, action.getTotal());
    }

    @Test
    public void readFromJsonObjectMissingPodcastReturnsNull() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("episode", EPISODE);
        json.put("action", "play");

        assertNull(EpisodeAction.readFromJsonObject(json));
    }

    @Test
    public void readFromJsonObjectMissingEpisodeReturnsNull() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("podcast", PODCAST);
        json.put("action", "play");

        assertNull(EpisodeAction.readFromJsonObject(json));
    }

    @Test
    public void readFromJsonObjectMissingActionReturnsNull() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("podcast", PODCAST);
        json.put("episode", EPISODE);

        assertNull(EpisodeAction.readFromJsonObject(json));
    }

    @Test
    public void readFromJsonObjectEmptyPodcastReturnsNull() throws JSONException {
        JSONObject json = baseJson("play");
        json.put("podcast", "");

        assertNull(EpisodeAction.readFromJsonObject(json));
    }

    @Test
    public void readFromJsonObjectUnknownActionReturnsNull() throws JSONException {
        JSONObject json = baseJson("not_a_real_action");

        assertNull(EpisodeAction.readFromJsonObject(json));
    }

    @Test
    public void readFromJsonObjectActionIsCaseInsensitive() throws JSONException {
        JSONObject json = baseJson("PlAy");

        EpisodeAction action = EpisodeAction.readFromJsonObject(json);

        assertEquals(EpisodeAction.Action.PLAY, action.getAction());
    }

    @Test
    public void readFromJsonObjectJsonNullActionReturnsNull() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("podcast", PODCAST);
        json.put("episode", EPISODE);
        json.put("action", JSONObject.NULL);

        assertNull(EpisodeAction.readFromJsonObject(json));
    }

    @Test
    public void readFromJsonObjectAbsentTimestampLeavesTimestampNull() throws JSONException {
        JSONObject json = baseJson("play");

        EpisodeAction action = EpisodeAction.readFromJsonObject(json);

        assertNull(action.getTimestamp());
    }

    @Test
    public void readFromJsonObjectUnparseableTimestampIsSwallowedAndLeavesTimestampNull() throws JSONException {
        JSONObject json = baseJson("play");
        json.put("timestamp", "not-a-date");

        EpisodeAction action = EpisodeAction.readFromJsonObject(json);

        assertNull(action.getTimestamp());
    }

    @Test
    public void readFromJsonObjectParsesTimestampAsUtc() throws JSONException {
        JSONObject json = baseJson("play");
        json.put("timestamp", "2023-11-20T18:25:10");

        EpisodeAction action = EpisodeAction.readFromJsonObject(json);

        assertEquals(epochMillisUtc("2023-11-20T18:25:10"), action.getTimestamp().getTime());
    }

    @Test
    public void readFromJsonObjectEmptyGuidLeavesGuidNull() throws JSONException {
        JSONObject json = baseJson("play");
        json.put("guid", "");

        EpisodeAction action = EpisodeAction.readFromJsonObject(json);

        assertNull(action.getGuid());
    }

    @Test
    public void readFromJsonObjectSetsGuidWhenPresent() throws JSONException {
        JSONObject json = baseJson("play");
        json.put("guid", "guid-abc");

        EpisodeAction action = EpisodeAction.readFromJsonObject(json);

        assertEquals("guid-abc", action.getGuid());
    }

    @Test
    public void readFromJsonObjectPlayFieldsAllOrNothingWhenPositionIsZero() throws JSONException {
        JSONObject json = baseJson("play");
        json.put("started", 5);
        json.put("position", 0);
        json.put("total", 100);

        EpisodeAction action = EpisodeAction.readFromJsonObject(json);

        assertEquals(-1, action.getStarted());
        assertEquals(-1, action.getPosition());
        assertEquals(-1, action.getTotal());
    }

    @Test
    public void readFromJsonObjectPlayFieldsAllOrNothingWhenTotalIsZero() throws JSONException {
        JSONObject json = baseJson("play");
        json.put("started", 5);
        json.put("position", 10);
        json.put("total", 0);

        EpisodeAction action = EpisodeAction.readFromJsonObject(json);

        assertEquals(-1, action.getStarted());
        assertEquals(-1, action.getPosition());
        assertEquals(-1, action.getTotal());
    }

    @Test
    public void readFromJsonObjectPlayFieldsAcceptStartedZero() throws JSONException {
        JSONObject json = baseJson("play");
        json.put("started", 0);
        json.put("position", 10);
        json.put("total", 100);

        EpisodeAction action = EpisodeAction.readFromJsonObject(json);

        assertEquals(0, action.getStarted());
        assertEquals(10, action.getPosition());
        assertEquals(100, action.getTotal());
    }

    @Test
    public void readFromJsonObjectIgnoresPlayFieldsForNonPlayAction() throws JSONException {
        JSONObject json = baseJson("new");
        json.put("started", 1);
        json.put("position", 2);
        json.put("total", 3);

        EpisodeAction action = EpisodeAction.readFromJsonObject(json);

        assertEquals(-1, action.getStarted());
        assertEquals(-1, action.getPosition());
        assertEquals(-1, action.getTotal());
    }

    @Test
    public void readFromJsonObjectMissingPlayFieldsDefaultToMinusOne() throws JSONException {
        JSONObject json = baseJson("play");

        EpisodeAction action = EpisodeAction.readFromJsonObject(json);

        assertEquals(-1, action.getStarted());
        assertEquals(-1, action.getPosition());
        assertEquals(-1, action.getTotal());
    }

    // ---- write side (7) ----

    @Test
    public void writeToJsonObjectProducesGoldenPlayJson() throws JSONException {
        Date timestamp = dateAtUtc(2022, 5, 6, 7, 8, 9);
        EpisodeAction action = new EpisodeAction.Builder(PODCAST, EPISODE, EpisodeAction.Action.PLAY)
                .guid("guid-42")
                .timestamp(timestamp)
                .started(1)
                .position(2)
                .total(3)
                .build();

        JSONObject json = action.writeToJsonObject();

        assertEquals(PODCAST, json.getString("podcast"));
        assertEquals(EPISODE, json.getString("episode"));
        assertEquals("guid-42", json.getString("guid"));
        assertEquals("play", json.getString("action"));
        String expectedTimestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
                .withZone(ZoneOffset.UTC)
                .format(timestamp.toInstant());
        assertEquals(expectedTimestamp, json.getString("timestamp"));
        assertEquals(1, json.getInt("started"));
        assertEquals(2, json.getInt("position"));
        assertEquals(3, json.getInt("total"));
    }

    @Test
    public void writeToJsonObjectOmitsNullPodcastEpisodeAndGuid() throws JSONException {
        EpisodeAction action = new EpisodeAction.Builder(null, null, EpisodeAction.Action.PLAY)
                .currentTimestamp()
                .build();

        JSONObject json = action.writeToJsonObject();

        assertFalse(json.has("podcast"));
        assertFalse(json.has("episode"));
        assertFalse(json.has("guid"));
    }

    @Test
    public void writeToJsonObjectAlwaysWritesActionKey() throws JSONException {
        EpisodeAction action = new EpisodeAction.Builder(null, null, EpisodeAction.Action.NEW)
                .currentTimestamp()
                .build();

        JSONObject json = action.writeToJsonObject();

        assertTrue(json.has("action"));
        assertEquals("new", json.getString("action"));
    }

    @Test
    public void writeToJsonObjectWritesLowercaseActionString() throws JSONException {
        EpisodeAction action = new EpisodeAction.Builder(PODCAST, EPISODE, EpisodeAction.Action.DOWNLOAD)
                .currentTimestamp()
                .build();

        JSONObject json = action.writeToJsonObject();

        assertEquals("download", json.getString("action"));
    }

    @Test
    public void writeToJsonObjectOmitsPlayFieldsForNonPlayAction() throws JSONException {
        EpisodeAction action = new EpisodeAction.Builder(PODCAST, EPISODE, EpisodeAction.Action.DELETE)
                .currentTimestamp()
                .build();

        JSONObject json = action.writeToJsonObject();

        assertFalse(json.has("started"));
        assertFalse(json.has("position"));
        assertFalse(json.has("total"));
    }

    @Test
    public void writeToJsonObjectWithNullTimestampThrowsNullPointerException() {
        EpisodeAction action = new EpisodeAction.Builder(PODCAST, EPISODE, EpisodeAction.Action.PLAY).build();

        assertThrows(NullPointerException.class, action::writeToJsonObject);
    }

    @Test
    public void writeToJsonObjectWithNullActionThrowsNullPointerException() {
        EpisodeAction action = new EpisodeAction.Builder(PODCAST, EPISODE, null)
                .currentTimestamp()
                .build();

        assertThrows(NullPointerException.class, action::writeToJsonObject);
    }

    // ---- round trip (3) ----

    @Test
    public void writeThenReadRoundTripPreservesAllFieldsForPlayAction() throws JSONException {
        Date timestamp = dateAtUtc(2022, 5, 6, 7, 8, 9);
        EpisodeAction original = new EpisodeAction.Builder(PODCAST, EPISODE, EpisodeAction.Action.PLAY)
                .guid("guid-42")
                .timestamp(timestamp)
                .started(5)
                .position(42)
                .total(600)
                .build();

        JSONObject json = original.writeToJsonObject();
        EpisodeAction roundTripped = EpisodeAction.readFromJsonObject(json);

        assertEquals(original.getPodcast(), roundTripped.getPodcast());
        assertEquals(original.getEpisode(), roundTripped.getEpisode());
        assertEquals(original.getGuid(), roundTripped.getGuid());
        assertEquals(original.getAction(), roundTripped.getAction());
        assertEquals(original.getTimestamp(), roundTripped.getTimestamp());
        assertEquals(original.getStarted(), roundTripped.getStarted());
        assertEquals(original.getPosition(), roundTripped.getPosition());
        assertEquals(original.getTotal(), roundTripped.getTotal());
    }

    @Test
    public void writeThenReadRoundTripPreservesAllFieldsForNonPlayAction() throws JSONException {
        Date timestamp = dateAtUtc(2022, 5, 6, 7, 8, 9);
        EpisodeAction original = new EpisodeAction.Builder(PODCAST, EPISODE, EpisodeAction.Action.DELETE)
                .guid("guid-99")
                .timestamp(timestamp)
                .build();

        JSONObject json = original.writeToJsonObject();
        EpisodeAction roundTripped = EpisodeAction.readFromJsonObject(json);

        assertEquals(original.getPodcast(), roundTripped.getPodcast());
        assertEquals(original.getEpisode(), roundTripped.getEpisode());
        assertEquals(original.getGuid(), roundTripped.getGuid());
        assertEquals(original.getAction(), roundTripped.getAction());
        assertEquals(original.getTimestamp(), roundTripped.getTimestamp());
        assertEquals(-1, roundTripped.getStarted());
        assertEquals(-1, roundTripped.getPosition());
        assertEquals(-1, roundTripped.getTotal());
    }

    @Test
    public void writeThenReadRoundTripTruncatesSubSecondPrecision() throws JSONException, DateTimeException {
        Date timestampWithMillis = dateAtUtcWithNanos(2022, 5, 6, 7, 8, 9, 789_000_000);
        EpisodeAction original = new EpisodeAction.Builder(PODCAST, EPISODE, EpisodeAction.Action.PLAY)
                .timestamp(timestampWithMillis)
                .started(1)
                .position(2)
                .total(3)
                .build();

        JSONObject json = original.writeToJsonObject();
        EpisodeAction roundTripped = EpisodeAction.readFromJsonObject(json);

        Instant expectedTruncated = dateAtUtc(2022, 5, 6, 7, 8, 9).toInstant();
        assertEquals(expectedTruncated.toEpochMilli(), roundTripped.getTimestamp().getTime());
        assertNotEquals(original.getTimestamp().getTime(), roundTripped.getTimestamp().getTime());
    }
}
