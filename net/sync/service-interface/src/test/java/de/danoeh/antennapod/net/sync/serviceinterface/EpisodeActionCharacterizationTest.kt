package de.danoeh.antennapod.net.sync.serviceinterface

import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Characterizes [EpisodeAction]'s builder, getters, static Action aliases,
 * toString, and the known equals/hashCode defect at
 * EpisodeAction.java:161 (action != that.action, where every sibling clause is an
 * equality test). This suite pins today's behaviour, defect included; it does not endorse the
 * defect, and fixing it is out of scope for this migration (see the module README and future-work
 * item 9).
 */
@RunWith(RobolectricTestRunner::class)
class EpisodeActionCharacterizationTest {

    // ---- Builder (14) ----

    @Test
    fun builderFeedItemConstructorReadsFeedAndMediaDownloadUrlsAndItemIdentifier() {
        val item = validItem()

        val action = EpisodeAction.Builder(item, EpisodeAction.Action.PLAY).build()

        assertEquals(PODCAST_URL, action.podcast)
        assertEquals(EPISODE_URL, action.episode)
        assertEquals("guid-item-1", action.guid)
        assertEquals(EpisodeAction.Action.PLAY, action.action)
    }

    @Test
    fun builderFeedItemConstructorWithNullFeedThrowsNullPointerException() {
        val item = FeedItem()
        item.feed = null
        item.media = FeedMedia(item, EPISODE_URL, 0L, "audio/mp3")

        assertThrows(NullPointerException::class.java) { EpisodeAction.Builder(item, EpisodeAction.Action.PLAY) }
    }

    @Test
    fun builderFeedItemConstructorWithNullMediaThrowsNullPointerException() {
        val item = FeedItem()
        item.feed = Feed(PODCAST_URL, null)
        item.media = null

        assertThrows(NullPointerException::class.java) { EpisodeAction.Builder(item, EpisodeAction.Action.PLAY) }
    }

    @Test
    fun builderFeedItemConstructorWithNullItemIdentifierLeavesGuidNull() {
        val item = validItem()
        item.itemIdentifier = null

        val action = EpisodeAction.Builder(item, EpisodeAction.Action.PLAY).build()

        assertNull(action.guid)
    }

    @Test
    fun builderStringConstructorAcceptsNullPodcastAndEpisode() {
        val action = EpisodeAction.Builder(null, null, EpisodeAction.Action.PLAY).build()

        assertNull(action.podcast)
        assertNull(action.episode)
    }

    @Test
    fun builderStringConstructorAcceptsNullAction() {
        val action = EpisodeAction.Builder(PODCAST_URL, EPISODE_URL, null).build()

        assertNull(action.action)
    }

    @Test
    fun builderFeedItemConstructorAcceptsNullAction() {
        val item = validItem()

        val action = EpisodeAction.Builder(item, null).build()

        assertNull(action.action)
    }

    @Test
    fun builderDefaultsStartedPositionTotalToMinusOne() {
        val action = EpisodeAction.Builder(PODCAST_URL, EPISODE_URL, EpisodeAction.Action.PLAY)
            .build()

        assertEquals(-1, action.started)
        assertEquals(-1, action.position)
        assertEquals(-1, action.total)
    }

    @Test
    fun builderIgnoresStartedPositionTotalForNonPlayAction() {
        val action = EpisodeAction.Builder(PODCAST_URL, EPISODE_URL, EpisodeAction.Action.NEW)
            .started(5)
            .position(10)
            .total(100)
            .build()

        assertEquals(-1, action.started)
        assertEquals(-1, action.position)
        assertEquals(-1, action.total)
    }

    @Test
    fun builderAppliesStartedPositionTotalForPlayAction() {
        val action = EpisodeAction.Builder(PODCAST_URL, EPISODE_URL, EpisodeAction.Action.PLAY)
            .started(5)
            .position(10)
            .total(100)
            .build()

        assertEquals(5, action.started)
        assertEquals(10, action.position)
        assertEquals(100, action.total)
    }

    @Test
    fun builderCurrentTimestampSetsNonNullTimestamp() {
        val action = EpisodeAction.Builder(PODCAST_URL, EPISODE_URL, EpisodeAction.Action.PLAY)
            .currentTimestamp()
            .build()

        assertNotNull(action.timestamp)
    }

    @Test
    fun builderTimestampAcceptsNull() {
        val action = EpisodeAction.Builder(PODCAST_URL, EPISODE_URL, EpisodeAction.Action.PLAY)
            .timestamp(null)
            .build()

        assertNull(action.timestamp)
    }

    @Test
    fun builderGuidSetsGuid() {
        val action = EpisodeAction.Builder(PODCAST_URL, EPISODE_URL, EpisodeAction.Action.PLAY)
            .guid("guid-x")
            .build()

        assertEquals("guid-x", action.guid)
    }

    @Test
    fun gettersReturnConstructedValues() {
        val timestamp = dateAtUtc(2022, 1, 2, 3, 4, 5)
        val action = EpisodeAction.Builder(PODCAST_URL, EPISODE_URL, EpisodeAction.Action.PLAY)
            .guid("guid-y")
            .timestamp(timestamp)
            .started(1)
            .position(2)
            .total(3)
            .build()

        assertEquals(PODCAST_URL, action.podcast)
        assertEquals(EPISODE_URL, action.episode)
        assertEquals("guid-y", action.guid)
        assertEquals(EpisodeAction.Action.PLAY, action.action)
        assertEquals(timestamp, action.timestamp)
        assertEquals(1, action.started)
        assertEquals(2, action.position)
        assertEquals(3, action.total)
    }

    // ---- Static Action aliases (2) ----

    @Test
    fun staticActionAliasesMatchNestedEnumConstants() {
        assertSame(EpisodeAction.Action.NEW, EpisodeAction.NEW)
        assertSame(EpisodeAction.Action.DOWNLOAD, EpisodeAction.DOWNLOAD)
        assertSame(EpisodeAction.Action.PLAY, EpisodeAction.PLAY)
        assertSame(EpisodeAction.Action.DELETE, EpisodeAction.DELETE)
    }

    @Test
    fun staticActionAliasesAreDeclaredAsPublicStaticFinalFields() {
        for (fieldName in arrayOf("NEW", "DOWNLOAD", "PLAY", "DELETE")) {
            val field: Field = EpisodeAction::class.java.getField(fieldName)
            val modifiers = field.modifiers
            assertTrue(fieldName + " must be public", Modifier.isPublic(modifiers))
            assertTrue(fieldName + " must be static", Modifier.isStatic(modifiers))
            assertTrue(fieldName + " must be final", Modifier.isFinal(modifiers))
        }
    }

    // ---- equals/hashCode: pinning a known defect (7) ----
    // EpisodeAction.java:161 reads action != that.action where every sibling clause is an
    // equality test. Consequence: field-identical instances compare unequal, and instances
    // differing only in action compare equal while having different hash codes. This suite
    // pins that behaviour as-is; it is not fixed in this migration (see README, future-work #9).

    @Test
    fun equalsReturnsFalseForFieldIdenticalInstances() {
        val timestamp = dateAtUtc(2022, 1, 2, 3, 4, 5)
        val a = EpisodeAction.Builder(PODCAST_URL, EPISODE_URL, EpisodeAction.Action.PLAY)
            .guid("g").timestamp(timestamp).started(1).position(2).total(3).build()
        val b = EpisodeAction.Builder(PODCAST_URL, EPISODE_URL, EpisodeAction.Action.PLAY)
            .guid("g").timestamp(timestamp).started(1).position(2).total(3).build()

        assertNotEquals(a, b)
    }

    @Test
    fun equalsReturnsTrueForInstancesDifferingOnlyInAction() {
        val timestamp = dateAtUtc(2022, 1, 2, 3, 4, 5)
        val a = EpisodeAction.Builder(PODCAST_URL, EPISODE_URL, EpisodeAction.Action.NEW)
            .guid("g").timestamp(timestamp).build()
        val b = EpisodeAction.Builder(PODCAST_URL, EPISODE_URL, EpisodeAction.Action.DOWNLOAD)
            .guid("g").timestamp(timestamp).build()

        assertEquals(a, b)
    }

    @Test
    fun hashCodeDiffersForInstancesThatEqualsCallsEqual() {
        val timestamp = dateAtUtc(2022, 1, 2, 3, 4, 5)
        val a = EpisodeAction.Builder(PODCAST_URL, EPISODE_URL, EpisodeAction.Action.NEW)
            .guid("g").timestamp(timestamp).build()
        val b = EpisodeAction.Builder(PODCAST_URL, EPISODE_URL, EpisodeAction.Action.DOWNLOAD)
            .guid("g").timestamp(timestamp).build()

        assertEquals(a, b)
        assertNotEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun equalsIsTrueForSameInstance() {
        val a = EpisodeAction.Builder(PODCAST_URL, EPISODE_URL, EpisodeAction.Action.PLAY).build()

        assertEquals(a, a)
    }

    @Test
    fun equalsIsFalseForNullAndForOtherTypes() {
        val a = EpisodeAction.Builder(PODCAST_URL, EPISODE_URL, EpisodeAction.Action.PLAY).build()

        assertNotEquals(a, null)
        assertNotEquals(a, "not an episode action")
    }

    @Test
    fun hashCodeOfFieldIdenticalInstancesIsEqual() {
        val timestamp = dateAtUtc(2022, 1, 2, 3, 4, 5)
        val a = EpisodeAction.Builder(PODCAST_URL, EPISODE_URL, EpisodeAction.Action.PLAY)
            .guid("g").timestamp(timestamp).started(1).position(2).total(3).build()
        val b = EpisodeAction.Builder(PODCAST_URL, EPISODE_URL, EpisodeAction.Action.PLAY)
            .guid("g").timestamp(timestamp).started(1).position(2).total(3).build()

        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun hashCodeHandlesAllNullReferenceFields() {
        val action = EpisodeAction.Builder(null, null, null).build()

        assertEquals(-993, action.hashCode())
    }

    // ---- toString (2) ----

    @Test
    fun toStringMatchesExactFormat() {
        val timestamp = dateAtUtc(2022, 1, 2, 3, 4, 5)
        val action = EpisodeAction.Builder(PODCAST_URL, EPISODE_URL, EpisodeAction.Action.PLAY)
            .guid("g").timestamp(timestamp).started(1).position(2).total(3).build()

        val expected = "EpisodeAction{podcast='" + PODCAST_URL + "', episode='" + EPISODE_URL + "', guid='g'" +
            ", action=PLAY, timestamp=" + timestamp + ", started=1, position=2, total=3}"
        assertEquals(expected, action.toString())
    }

    @Test
    fun toStringRendersNullFieldsAsNull() {
        val action = EpisodeAction.Builder(null, null, null).build()

        val expected = "EpisodeAction{podcast='null', episode='null', guid='null', action=null, " +
            "timestamp=null, started=-1, position=-1, total=-1}"
        assertEquals(expected, action.toString())
        assertFalse(action.toString().isEmpty())
    }

    private companion object {
        private const val PODCAST_URL = "https://example.com/feed.xml"
        private const val EPISODE_URL = "https://example.com/episode.mp3"

        private fun validItem(): FeedItem {
            val item = FeedItem()
            item.feed = Feed(PODCAST_URL, null)
            item.media = FeedMedia(item, EPISODE_URL, 0L, "audio/mp3")
            item.itemIdentifier = "guid-item-1"
            return item
        }

        private fun dateAtUtc(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Date {
            return Date.from(LocalDateTime.of(year, month, day, hour, minute, second).toInstant(ZoneOffset.UTC))
        }
    }
}
