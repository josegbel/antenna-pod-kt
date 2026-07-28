package de.danoeh.antennapod.net.sync.serviceinterface;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;

/**
 * Characterizes {@link EpisodeAction}'s builder, getters, static {@code Action} aliases,
 * {@code toString}, and the known {@code equals}/{@code hashCode} defect at
 * {@code EpisodeAction.java:161} ({@code action != that.action}, where every sibling clause is an
 * equality test). This suite pins today's behaviour, defect included; it does not endorse the
 * defect, and fixing it is out of scope for this migration (see the module README and future-work
 * item 9).
 */
@RunWith(RobolectricTestRunner.class)
public class EpisodeActionCharacterizationTest {

    private static final String PODCAST_URL = "https://example.com/feed.xml";
    private static final String EPISODE_URL = "https://example.com/episode.mp3";

    private static FeedItem validItem() {
        FeedItem item = new FeedItem();
        item.setFeed(new Feed(PODCAST_URL, null));
        item.setMedia(new FeedMedia(item, EPISODE_URL, 0L, "audio/mp3"));
        item.setItemIdentifier("guid-item-1");
        return item;
    }

    private static Date dateAtUtc(int year, int month, int day, int hour, int minute, int second) {
        return Date.from(LocalDateTime.of(year, month, day, hour, minute, second).toInstant(ZoneOffset.UTC));
    }

    // ---- Builder (14) ----

    @Test
    public void builderFeedItemConstructorReadsFeedAndMediaDownloadUrlsAndItemIdentifier() {
        FeedItem item = validItem();

        EpisodeAction action = new EpisodeAction.Builder(item, EpisodeAction.Action.PLAY).build();

        assertEquals(PODCAST_URL, action.getPodcast());
        assertEquals(EPISODE_URL, action.getEpisode());
        assertEquals("guid-item-1", action.getGuid());
        assertEquals(EpisodeAction.Action.PLAY, action.getAction());
    }

    @Test
    public void builderFeedItemConstructorWithNullFeedThrowsNullPointerException() {
        FeedItem item = new FeedItem();
        item.setFeed(null);
        item.setMedia(new FeedMedia(item, EPISODE_URL, 0L, "audio/mp3"));

        assertThrows(NullPointerException.class,
                () -> new EpisodeAction.Builder(item, EpisodeAction.Action.PLAY));
    }

    @Test
    public void builderFeedItemConstructorWithNullMediaThrowsNullPointerException() {
        FeedItem item = new FeedItem();
        item.setFeed(new Feed(PODCAST_URL, null));
        item.setMedia(null);

        assertThrows(NullPointerException.class,
                () -> new EpisodeAction.Builder(item, EpisodeAction.Action.PLAY));
    }

    @Test
    public void builderFeedItemConstructorWithNullItemIdentifierLeavesGuidNull() {
        FeedItem item = validItem();
        item.setItemIdentifier(null);

        EpisodeAction action = new EpisodeAction.Builder(item, EpisodeAction.Action.PLAY).build();

        assertNull(action.getGuid());
    }

    @Test
    public void builderStringConstructorAcceptsNullPodcastAndEpisode() {
        EpisodeAction action = new EpisodeAction.Builder(null, null, EpisodeAction.Action.PLAY).build();

        assertNull(action.getPodcast());
        assertNull(action.getEpisode());
    }

    @Test
    public void builderStringConstructorAcceptsNullAction() {
        EpisodeAction action = new EpisodeAction.Builder(PODCAST_URL, EPISODE_URL, null).build();

        assertNull(action.getAction());
    }

    @Test
    public void builderFeedItemConstructorAcceptsNullAction() {
        FeedItem item = validItem();

        EpisodeAction action = new EpisodeAction.Builder(item, null).build();

        assertNull(action.getAction());
    }

    @Test
    public void builderDefaultsStartedPositionTotalToMinusOne() {
        EpisodeAction action = new EpisodeAction.Builder(PODCAST_URL, EPISODE_URL, EpisodeAction.Action.PLAY)
                .build();

        assertEquals(-1, action.getStarted());
        assertEquals(-1, action.getPosition());
        assertEquals(-1, action.getTotal());
    }

    @Test
    public void builderIgnoresStartedPositionTotalForNonPlayAction() {
        EpisodeAction action = new EpisodeAction.Builder(PODCAST_URL, EPISODE_URL, EpisodeAction.Action.NEW)
                .started(5)
                .position(10)
                .total(100)
                .build();

        assertEquals(-1, action.getStarted());
        assertEquals(-1, action.getPosition());
        assertEquals(-1, action.getTotal());
    }

    @Test
    public void builderAppliesStartedPositionTotalForPlayAction() {
        EpisodeAction action = new EpisodeAction.Builder(PODCAST_URL, EPISODE_URL, EpisodeAction.Action.PLAY)
                .started(5)
                .position(10)
                .total(100)
                .build();

        assertEquals(5, action.getStarted());
        assertEquals(10, action.getPosition());
        assertEquals(100, action.getTotal());
    }

    @Test
    public void builderCurrentTimestampSetsNonNullTimestamp() {
        EpisodeAction action = new EpisodeAction.Builder(PODCAST_URL, EPISODE_URL, EpisodeAction.Action.PLAY)
                .currentTimestamp()
                .build();

        assertNotNull(action.getTimestamp());
    }

    @Test
    public void builderTimestampAcceptsNull() {
        EpisodeAction action = new EpisodeAction.Builder(PODCAST_URL, EPISODE_URL, EpisodeAction.Action.PLAY)
                .timestamp(null)
                .build();

        assertNull(action.getTimestamp());
    }

    @Test
    public void builderGuidSetsGuid() {
        EpisodeAction action = new EpisodeAction.Builder(PODCAST_URL, EPISODE_URL, EpisodeAction.Action.PLAY)
                .guid("guid-x")
                .build();

        assertEquals("guid-x", action.getGuid());
    }

    @Test
    public void gettersReturnConstructedValues() {
        Date timestamp = dateAtUtc(2022, 1, 2, 3, 4, 5);
        EpisodeAction action = new EpisodeAction.Builder(PODCAST_URL, EPISODE_URL, EpisodeAction.Action.PLAY)
                .guid("guid-y")
                .timestamp(timestamp)
                .started(1)
                .position(2)
                .total(3)
                .build();

        assertEquals(PODCAST_URL, action.getPodcast());
        assertEquals(EPISODE_URL, action.getEpisode());
        assertEquals("guid-y", action.getGuid());
        assertEquals(EpisodeAction.Action.PLAY, action.getAction());
        assertEquals(timestamp, action.getTimestamp());
        assertEquals(1, action.getStarted());
        assertEquals(2, action.getPosition());
        assertEquals(3, action.getTotal());
    }

    // ---- Static Action aliases (2) ----

    @Test
    public void staticActionAliasesMatchNestedEnumConstants() {
        assertSame(EpisodeAction.Action.NEW, EpisodeAction.NEW);
        assertSame(EpisodeAction.Action.DOWNLOAD, EpisodeAction.DOWNLOAD);
        assertSame(EpisodeAction.Action.PLAY, EpisodeAction.PLAY);
        assertSame(EpisodeAction.Action.DELETE, EpisodeAction.DELETE);
    }

    @Test
    public void staticActionAliasesAreDeclaredAsPublicStaticFinalFields() throws NoSuchFieldException {
        for (String fieldName : new String[] {"NEW", "DOWNLOAD", "PLAY", "DELETE"}) {
            Field field = EpisodeAction.class.getField(fieldName);
            int modifiers = field.getModifiers();
            assertTrue(fieldName + " must be public", Modifier.isPublic(modifiers));
            assertTrue(fieldName + " must be static", Modifier.isStatic(modifiers));
            assertTrue(fieldName + " must be final", Modifier.isFinal(modifiers));
        }
    }

    // ---- equals/hashCode: pinning a known defect (7) ----
    // EpisodeAction.java:161 reads `action != that.action` where every sibling clause is an
    // equality test. Consequence: field-identical instances compare unequal, and instances
    // differing only in `action` compare equal while having different hash codes. This suite
    // pins that behaviour as-is; it is not fixed in this migration (see README, future-work #9).

    @Test
    public void equalsReturnsFalseForFieldIdenticalInstances() {
        Date timestamp = dateAtUtc(2022, 1, 2, 3, 4, 5);
        EpisodeAction a = new EpisodeAction.Builder(PODCAST_URL, EPISODE_URL, EpisodeAction.Action.PLAY)
                .guid("g").timestamp(timestamp).started(1).position(2).total(3).build();
        EpisodeAction b = new EpisodeAction.Builder(PODCAST_URL, EPISODE_URL, EpisodeAction.Action.PLAY)
                .guid("g").timestamp(timestamp).started(1).position(2).total(3).build();

        assertNotEquals(a, b);
    }

    @Test
    public void equalsReturnsTrueForInstancesDifferingOnlyInAction() {
        Date timestamp = dateAtUtc(2022, 1, 2, 3, 4, 5);
        EpisodeAction a = new EpisodeAction.Builder(PODCAST_URL, EPISODE_URL, EpisodeAction.Action.NEW)
                .guid("g").timestamp(timestamp).build();
        EpisodeAction b = new EpisodeAction.Builder(PODCAST_URL, EPISODE_URL, EpisodeAction.Action.DOWNLOAD)
                .guid("g").timestamp(timestamp).build();

        assertEquals(a, b);
    }

    @Test
    public void hashCodeDiffersForInstancesThatEqualsCallsEqual() {
        Date timestamp = dateAtUtc(2022, 1, 2, 3, 4, 5);
        EpisodeAction a = new EpisodeAction.Builder(PODCAST_URL, EPISODE_URL, EpisodeAction.Action.NEW)
                .guid("g").timestamp(timestamp).build();
        EpisodeAction b = new EpisodeAction.Builder(PODCAST_URL, EPISODE_URL, EpisodeAction.Action.DOWNLOAD)
                .guid("g").timestamp(timestamp).build();

        assertEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalsIsTrueForSameInstance() {
        EpisodeAction a = new EpisodeAction.Builder(PODCAST_URL, EPISODE_URL, EpisodeAction.Action.PLAY).build();

        assertEquals(a, a);
    }

    @Test
    public void equalsIsFalseForNullAndForOtherTypes() {
        EpisodeAction a = new EpisodeAction.Builder(PODCAST_URL, EPISODE_URL, EpisodeAction.Action.PLAY).build();

        assertNotEquals(a, null);
        assertNotEquals(a, "not an episode action");
    }

    @Test
    public void hashCodeOfFieldIdenticalInstancesIsEqual() {
        Date timestamp = dateAtUtc(2022, 1, 2, 3, 4, 5);
        EpisodeAction a = new EpisodeAction.Builder(PODCAST_URL, EPISODE_URL, EpisodeAction.Action.PLAY)
                .guid("g").timestamp(timestamp).started(1).position(2).total(3).build();
        EpisodeAction b = new EpisodeAction.Builder(PODCAST_URL, EPISODE_URL, EpisodeAction.Action.PLAY)
                .guid("g").timestamp(timestamp).started(1).position(2).total(3).build();

        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void hashCodeHandlesAllNullReferenceFields() {
        EpisodeAction action = new EpisodeAction.Builder(null, null, null).build();

        assertEquals(-993, action.hashCode());
    }

    // ---- toString (2) ----

    @Test
    public void toStringMatchesExactFormat() {
        Date timestamp = dateAtUtc(2022, 1, 2, 3, 4, 5);
        EpisodeAction action = new EpisodeAction.Builder(PODCAST_URL, EPISODE_URL, EpisodeAction.Action.PLAY)
                .guid("g").timestamp(timestamp).started(1).position(2).total(3).build();

        String expected = "EpisodeAction{podcast='" + PODCAST_URL + "', episode='" + EPISODE_URL + "', guid='g'"
                + ", action=PLAY, timestamp=" + timestamp + ", started=1, position=2, total=3}";
        assertEquals(expected, action.toString());
    }

    @Test
    public void toStringRendersNullFieldsAsNull() {
        EpisodeAction action = new EpisodeAction.Builder(null, null, null).build();

        String expected = "EpisodeAction{podcast='null', episode='null', guid='null', action=null, "
                + "timestamp=null, started=-1, position=-1, total=-1}";
        assertEquals(expected, action.toString());
        assertFalse(action.toString().isEmpty());
    }
}
