package de.danoeh.antennapod.net.sync.serviceinterface

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Characterizes [EpisodeAction.readFromJsonObject] and
 * [EpisodeAction.writeToJsonObject] against the live Java implementation. These two
 * methods define both the gpodder.net/Nextcloud wire format and the on-disk SharedPreferences
 * format of the user's pending-sync queue, and nothing in the repository tested them before this
 * suite. Runs under Robolectric because org.json.JSONObject is an Android-framework stub under
 * plain JUnit.
 */
@RunWith(RobolectricTestRunner::class)
class EpisodeActionJsonTest {

    // ---- read side (18) ----

    @Test
    fun readFromJsonObjectParsesFullPlayAction() {
        val json = baseJson("play")
        json.put("timestamp", "2021-01-02T03:04:05")
        json.put("guid", "guid-1")
        json.put("started", 10)
        json.put("position", 20)
        json.put("total", 300)

        val action = EpisodeAction.readFromJsonObject(json)!!

        assertEquals(PODCAST, action.podcast)
        assertEquals(EPISODE, action.episode)
        assertEquals(EpisodeAction.Action.PLAY, action.action)
        assertEquals("guid-1", action.guid)
        assertEquals(epochMillisUtc("2021-01-02T03:04:05"), action.timestamp!!.time)
        assertEquals(10, action.started)
        assertEquals(20, action.position)
        assertEquals(300, action.total)
    }

    @Test
    fun readFromJsonObjectMissingPodcastReturnsNull() {
        val json = JSONObject()
        json.put("episode", EPISODE)
        json.put("action", "play")

        assertNull(EpisodeAction.readFromJsonObject(json))
    }

    @Test
    fun readFromJsonObjectMissingEpisodeReturnsNull() {
        val json = JSONObject()
        json.put("podcast", PODCAST)
        json.put("action", "play")

        assertNull(EpisodeAction.readFromJsonObject(json))
    }

    @Test
    fun readFromJsonObjectMissingActionReturnsNull() {
        val json = JSONObject()
        json.put("podcast", PODCAST)
        json.put("episode", EPISODE)

        assertNull(EpisodeAction.readFromJsonObject(json))
    }

    @Test
    fun readFromJsonObjectEmptyPodcastReturnsNull() {
        val json = baseJson("play")
        json.put("podcast", "")

        assertNull(EpisodeAction.readFromJsonObject(json))
    }

    @Test
    fun readFromJsonObjectUnknownActionReturnsNull() {
        val json = baseJson("not_a_real_action")

        assertNull(EpisodeAction.readFromJsonObject(json))
    }

    @Test
    fun readFromJsonObjectActionIsCaseInsensitive() {
        val json = baseJson("PlAy")

        val action = EpisodeAction.readFromJsonObject(json)!!

        assertEquals(EpisodeAction.Action.PLAY, action.action)
    }

    @Test
    fun readFromJsonObjectJsonNullActionReturnsNull() {
        val json = JSONObject()
        json.put("podcast", PODCAST)
        json.put("episode", EPISODE)
        json.put("action", JSONObject.NULL)

        assertNull(EpisodeAction.readFromJsonObject(json))
    }

    @Test
    fun readFromJsonObjectAbsentTimestampLeavesTimestampNull() {
        val json = baseJson("play")

        val action = EpisodeAction.readFromJsonObject(json)!!

        assertNull(action.timestamp)
    }

    @Test
    fun readFromJsonObjectUnparseableTimestampIsSwallowedAndLeavesTimestampNull() {
        val json = baseJson("play")
        json.put("timestamp", "not-a-date")

        val action = EpisodeAction.readFromJsonObject(json)!!

        assertNull(action.timestamp)
    }

    @Test
    fun readFromJsonObjectParsesTimestampAsUtc() {
        val json = baseJson("play")
        json.put("timestamp", "2023-11-20T18:25:10")

        val action = EpisodeAction.readFromJsonObject(json)!!

        assertEquals(epochMillisUtc("2023-11-20T18:25:10"), action.timestamp!!.time)
    }

    @Test
    fun readFromJsonObjectEmptyGuidLeavesGuidNull() {
        val json = baseJson("play")
        json.put("guid", "")

        val action = EpisodeAction.readFromJsonObject(json)!!

        assertNull(action.guid)
    }

    @Test
    fun readFromJsonObjectSetsGuidWhenPresent() {
        val json = baseJson("play")
        json.put("guid", "guid-abc")

        val action = EpisodeAction.readFromJsonObject(json)!!

        assertEquals("guid-abc", action.guid)
    }

    @Test
    fun readFromJsonObjectPlayFieldsAllOrNothingWhenPositionIsZero() {
        val json = baseJson("play")
        json.put("started", 5)
        json.put("position", 0)
        json.put("total", 100)

        val action = EpisodeAction.readFromJsonObject(json)!!

        assertEquals(-1, action.started)
        assertEquals(-1, action.position)
        assertEquals(-1, action.total)
    }

    @Test
    fun readFromJsonObjectPlayFieldsAllOrNothingWhenTotalIsZero() {
        val json = baseJson("play")
        json.put("started", 5)
        json.put("position", 10)
        json.put("total", 0)

        val action = EpisodeAction.readFromJsonObject(json)!!

        assertEquals(-1, action.started)
        assertEquals(-1, action.position)
        assertEquals(-1, action.total)
    }

    @Test
    fun readFromJsonObjectPlayFieldsAcceptStartedZero() {
        val json = baseJson("play")
        json.put("started", 0)
        json.put("position", 10)
        json.put("total", 100)

        val action = EpisodeAction.readFromJsonObject(json)!!

        assertEquals(0, action.started)
        assertEquals(10, action.position)
        assertEquals(100, action.total)
    }

    @Test
    fun readFromJsonObjectIgnoresPlayFieldsForNonPlayAction() {
        val json = baseJson("new")
        json.put("started", 1)
        json.put("position", 2)
        json.put("total", 3)

        val action = EpisodeAction.readFromJsonObject(json)!!

        assertEquals(-1, action.started)
        assertEquals(-1, action.position)
        assertEquals(-1, action.total)
    }

    @Test
    fun readFromJsonObjectMissingPlayFieldsDefaultToMinusOne() {
        val json = baseJson("play")

        val action = EpisodeAction.readFromJsonObject(json)!!

        assertEquals(-1, action.started)
        assertEquals(-1, action.position)
        assertEquals(-1, action.total)
    }

    // ---- write side (7) ----

    @Test
    fun writeToJsonObjectProducesGoldenPlayJson() {
        val timestamp = dateAtUtc(2022, 5, 6, 7, 8, 9)
        val action = EpisodeAction.Builder(PODCAST, EPISODE, EpisodeAction.Action.PLAY)
            .guid("guid-42")
            .timestamp(timestamp)
            .started(1)
            .position(2)
            .total(3)
            .build()

        val json = action.writeToJsonObject()!!

        assertEquals(PODCAST, json.getString("podcast"))
        assertEquals(EPISODE, json.getString("episode"))
        assertEquals("guid-42", json.getString("guid"))
        assertEquals("play", json.getString("action"))
        val expectedTimestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
            .withZone(ZoneOffset.UTC)
            .format(timestamp.toInstant())
        assertEquals(expectedTimestamp, json.getString("timestamp"))
        assertEquals(1, json.getInt("started"))
        assertEquals(2, json.getInt("position"))
        assertEquals(3, json.getInt("total"))
    }

    @Test
    fun writeToJsonObjectOmitsNullPodcastEpisodeAndGuid() {
        val action = EpisodeAction.Builder(null, null, EpisodeAction.Action.PLAY)
            .currentTimestamp()
            .build()

        val json = action.writeToJsonObject()!!

        assertFalse(json.has("podcast"))
        assertFalse(json.has("episode"))
        assertFalse(json.has("guid"))
    }

    @Test
    fun writeToJsonObjectAlwaysWritesActionKey() {
        val action = EpisodeAction.Builder(null, null, EpisodeAction.Action.NEW)
            .currentTimestamp()
            .build()

        val json = action.writeToJsonObject()!!

        assertTrue(json.has("action"))
        assertEquals("new", json.getString("action"))
    }

    @Test
    fun writeToJsonObjectWritesLowercaseActionString() {
        val action = EpisodeAction.Builder(PODCAST, EPISODE, EpisodeAction.Action.DOWNLOAD)
            .currentTimestamp()
            .build()

        val json = action.writeToJsonObject()!!

        assertEquals("download", json.getString("action"))
    }

    @Test
    fun writeToJsonObjectOmitsPlayFieldsForNonPlayAction() {
        val action = EpisodeAction.Builder(PODCAST, EPISODE, EpisodeAction.Action.DELETE)
            .currentTimestamp()
            .build()

        val json = action.writeToJsonObject()!!

        assertFalse(json.has("started"))
        assertFalse(json.has("position"))
        assertFalse(json.has("total"))
    }

    @Test
    fun writeToJsonObjectWithNullTimestampThrowsNullPointerException() {
        val action = EpisodeAction.Builder(PODCAST, EPISODE, EpisodeAction.Action.PLAY).build()

        assertThrows(NullPointerException::class.java, action::writeToJsonObject)
    }

    @Test
    fun writeToJsonObjectWithNullActionThrowsNullPointerException() {
        val action = EpisodeAction.Builder(PODCAST, EPISODE, null)
            .currentTimestamp()
            .build()

        assertThrows(NullPointerException::class.java, action::writeToJsonObject)
    }

    // ---- round trip (3) ----

    @Test
    fun writeThenReadRoundTripPreservesAllFieldsForPlayAction() {
        val timestamp = dateAtUtc(2022, 5, 6, 7, 8, 9)
        val original = EpisodeAction.Builder(PODCAST, EPISODE, EpisodeAction.Action.PLAY)
            .guid("guid-42")
            .timestamp(timestamp)
            .started(5)
            .position(42)
            .total(600)
            .build()

        val json = original.writeToJsonObject()!!
        val roundTripped = EpisodeAction.readFromJsonObject(json)!!

        assertEquals(original.podcast, roundTripped.podcast)
        assertEquals(original.episode, roundTripped.episode)
        assertEquals(original.guid, roundTripped.guid)
        assertEquals(original.action, roundTripped.action)
        assertEquals(original.timestamp, roundTripped.timestamp)
        assertEquals(original.started, roundTripped.started)
        assertEquals(original.position, roundTripped.position)
        assertEquals(original.total, roundTripped.total)
    }

    @Test
    fun writeThenReadRoundTripPreservesAllFieldsForNonPlayAction() {
        val timestamp = dateAtUtc(2022, 5, 6, 7, 8, 9)
        val original = EpisodeAction.Builder(PODCAST, EPISODE, EpisodeAction.Action.DELETE)
            .guid("guid-99")
            .timestamp(timestamp)
            .build()

        val json = original.writeToJsonObject()!!
        val roundTripped = EpisodeAction.readFromJsonObject(json)!!

        assertEquals(original.podcast, roundTripped.podcast)
        assertEquals(original.episode, roundTripped.episode)
        assertEquals(original.guid, roundTripped.guid)
        assertEquals(original.action, roundTripped.action)
        assertEquals(original.timestamp, roundTripped.timestamp)
        assertEquals(-1, roundTripped.started)
        assertEquals(-1, roundTripped.position)
        assertEquals(-1, roundTripped.total)
    }

    @Test
    fun writeThenReadRoundTripTruncatesSubSecondPrecision() {
        val timestampWithMillis = dateAtUtcWithNanos(2022, 5, 6, 7, 8, 9, 789_000_000)
        val original = EpisodeAction.Builder(PODCAST, EPISODE, EpisodeAction.Action.PLAY)
            .timestamp(timestampWithMillis)
            .started(1)
            .position(2)
            .total(3)
            .build()

        val json = original.writeToJsonObject()!!
        val roundTripped = EpisodeAction.readFromJsonObject(json)!!

        val expectedTruncated: Instant = dateAtUtc(2022, 5, 6, 7, 8, 9).toInstant()
        assertEquals(expectedTruncated.toEpochMilli(), roundTripped.timestamp!!.time)
        assertNotEquals(original.timestamp!!.time, roundTripped.timestamp!!.time)
    }

    private companion object {
        private const val PODCAST = "https://example.com/feed.xml"
        private const val EPISODE = "https://example.com/episode.mp3"

        private fun baseJson(action: String): JSONObject {
            val obj = JSONObject()
            obj.put("podcast", PODCAST)
            obj.put("episode", EPISODE)
            obj.put("action", action)
            return obj
        }

        private fun epochMillisUtc(isoLocalDateTime: String): Long {
            return LocalDateTime.parse(isoLocalDateTime).toInstant(ZoneOffset.UTC).toEpochMilli()
        }

        private fun dateAtUtc(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Date {
            return Date.from(LocalDateTime.of(year, month, day, hour, minute, second).toInstant(ZoneOffset.UTC))
        }

        private fun dateAtUtcWithNanos(
            year: Int,
            month: Int,
            day: Int,
            hour: Int,
            minute: Int,
            second: Int,
            nanoOfSecond: Int
        ): Date {
            return Date.from(
                LocalDateTime.of(year, month, day, hour, minute, second, nanoOfSecond).toInstant(ZoneOffset.UTC)
            )
        }
    }
}
