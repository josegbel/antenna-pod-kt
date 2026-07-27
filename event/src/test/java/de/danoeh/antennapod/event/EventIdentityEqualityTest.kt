package de.danoeh.antennapod.event

import de.danoeh.antennapod.event.playback.BufferUpdateEvent
import de.danoeh.antennapod.event.playback.PlaybackHistoryEvent
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent
import de.danoeh.antennapod.event.playback.PlaybackServiceEvent
import de.danoeh.antennapod.event.playback.SleepTimerUpdatedEvent
import de.danoeh.antennapod.event.playback.SpeedChangedEvent
import de.danoeh.antennapod.event.settings.SkipIntroEndingChangedEvent
import de.danoeh.antennapod.event.settings.SpeedPresetChangedEvent
import de.danoeh.antennapod.event.settings.VolumeAdaptionChangedEvent
import de.danoeh.antennapod.model.feed.FeedPreferences
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting
import java.util.Collections
import java.util.HashMap
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EventIdentityEqualityTest {

    private fun assertReferenceEquality(a: Any, b: Any) {
        assertNotSame(a, b)
        assertFalse(a.equals(b))
        assertNotEquals(a.hashCode(), b.hashCode())
    }

    private fun assertDefaultToStringShape(obj: Any) {
        val expected = obj.javaClass.name + "@" + Integer.toHexString(obj.hashCode())
        assertTrue(obj.toString().equals(expected))
    }

    @Test
    fun streamingConfirmationEventInstancesAreNotEqual() {
        val a = StreamingConfirmationEvent()
        val b = StreamingConfirmationEvent()
        assertReferenceEquality(a, b)
        assertDefaultToStringShape(a)
    }

    @Test
    fun playerStatusEventInstancesAreNotEqual() {
        val a = PlayerStatusEvent()
        val b = PlayerStatusEvent()
        assertReferenceEquality(a, b)
        assertDefaultToStringShape(a)
    }

    @Test
    fun discoveryDefaultUpdateEventInstancesAreNotEqual() {
        val a = DiscoveryDefaultUpdateEvent()
        val b = DiscoveryDefaultUpdateEvent()
        assertReferenceEquality(a, b)
        assertDefaultToStringShape(a)
    }

    @Test
    fun statisticsEventInstancesAreNotEqual() {
        val a = StatisticsEvent()
        val b = StatisticsEvent()
        assertReferenceEquality(a, b)
        assertDefaultToStringShape(a)
    }

    @Test
    fun playbackPositionEventInstancesAreNotEqual() {
        val a = PlaybackPositionEvent(1, 2)
        val b = PlaybackPositionEvent(1, 2)
        assertReferenceEquality(a, b)
        assertDefaultToStringShape(a)
    }

    @Test
    fun speedChangedEventInstancesAreNotEqual() {
        val a = SpeedChangedEvent(1.5f)
        val b = SpeedChangedEvent(1.5f)
        assertReferenceEquality(a, b)
        assertDefaultToStringShape(a)
    }

    @Test
    fun playerErrorEventInstancesAreNotEqual() {
        val a = PlayerErrorEvent("msg")
        val b = PlayerErrorEvent("msg")
        assertReferenceEquality(a, b)
        assertDefaultToStringShape(a)
    }

    @Test
    fun syncServiceEventInstancesAreNotEqual() {
        val a = SyncServiceEvent(1)
        val b = SyncServiceEvent(1)
        assertReferenceEquality(a, b)
        assertDefaultToStringShape(a)
    }

    @Test
    fun volumeAdaptionChangedEventInstancesAreNotEqual() {
        val a = VolumeAdaptionChangedEvent(VolumeAdaptionSetting.OFF, 1L)
        val b = VolumeAdaptionChangedEvent(VolumeAdaptionSetting.OFF, 1L)
        assertReferenceEquality(a, b)
        assertDefaultToStringShape(a)
    }

    @Test
    fun skipIntroEndingChangedEventInstancesAreNotEqual() {
        val a = SkipIntroEndingChangedEvent(1, 2, 3L)
        val b = SkipIntroEndingChangedEvent(1, 2, 3L)
        assertReferenceEquality(a, b)
        assertDefaultToStringShape(a)
    }

    @Test
    fun speedPresetChangedEventInstancesAreNotEqual() {
        val a = SpeedPresetChangedEvent(1f, 1L, FeedPreferences.SkipSilence.OFF)
        val b = SpeedPresetChangedEvent(1f, 1L, FeedPreferences.SkipSilence.OFF)
        assertReferenceEquality(a, b)
        assertDefaultToStringShape(a)
    }

    @Test
    fun feedUpdateRunningEventInstancesAreNotEqual() {
        val a = FeedUpdateRunningEvent(true)
        val b = FeedUpdateRunningEvent(true)
        assertReferenceEquality(a, b)
        assertDefaultToStringShape(a)
    }

    @Test
    fun playbackServiceEventInstancesAreNotEqual() {
        val a = PlaybackServiceEvent(PlaybackServiceEvent.Action.SERVICE_STARTED)
        val b = PlaybackServiceEvent(PlaybackServiceEvent.Action.SERVICE_STARTED)
        assertReferenceEquality(a, b)
        assertDefaultToStringShape(a)
    }

    @Test
    fun feedItemEventInstancesAreNotEqual() {
        val a = FeedItemEvent(Collections.emptyList(), false)
        val b = FeedItemEvent(Collections.emptyList(), false)
        assertReferenceEquality(a, b)
        assertDefaultToStringShape(a)
    }

    @Test
    fun queueEventInstancesAreNotEqual() {
        val a = QueueEvent.cleared()
        val b = QueueEvent.cleared()
        assertReferenceEquality(a, b)
        assertDefaultToStringShape(a)
    }

    @Test
    fun messageEventInstancesAreNotEqual() {
        val a = MessageEvent("hi")
        val b = MessageEvent("hi")
        assertReferenceEquality(a, b)
        assertDefaultToStringShape(a)
    }

    @Test
    fun bufferUpdateEventInstancesAreNotEqual() {
        val a = BufferUpdateEvent.started()
        val b = BufferUpdateEvent.started()
        assertReferenceEquality(a, b)
        assertDefaultToStringShape(a)
    }

    @Test
    fun sleepTimerUpdatedEventInstancesAreNotEqual() {
        val a = SleepTimerUpdatedEvent.cancelled()
        val b = SleepTimerUpdatedEvent.cancelled()
        assertReferenceEquality(a, b)
        assertDefaultToStringShape(a)
    }

    @Test
    fun episodeDownloadEventInstancesAreNotEqual() {
        val a = EpisodeDownloadEvent(HashMap())
        val b = EpisodeDownloadEvent(HashMap())
        assertReferenceEquality(a, b)
        assertDefaultToStringShape(a)
    }

    @Test
    fun feedListUpdateEventInstancesAreNotEqual() {
        val a = FeedListUpdateEvent(1L)
        val b = FeedListUpdateEvent(1L)
        assertReferenceEquality(a, b)
        assertDefaultToStringShape(a)
    }

    @Test
    fun feedEventInstancesAreNotEqualDespiteCustomToString() {
        val a = FeedEvent(FeedEvent.Action.FILTER_CHANGED, 1L)
        val b = FeedEvent(FeedEvent.Action.FILTER_CHANGED, 1L)
        assertReferenceEquality(a, b)
    }

    @Test
    fun downloadLogEventInstancesAreNotEqualDespiteCustomToString() {
        assertReferenceEquality(DownloadLogEvent.listUpdated(), DownloadLogEvent.listUpdated())
    }

    @Test
    fun playbackHistoryEventInstancesAreNotEqualDespiteCustomToString() {
        assertReferenceEquality(PlaybackHistoryEvent.listUpdated(), PlaybackHistoryEvent.listUpdated())
    }
}
