package de.danoeh.antennapod.event;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;

import de.danoeh.antennapod.event.playback.BufferUpdateEvent;
import de.danoeh.antennapod.event.playback.PlaybackHistoryEvent;
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent;
import de.danoeh.antennapod.event.playback.PlaybackServiceEvent;
import de.danoeh.antennapod.event.playback.SleepTimerUpdatedEvent;
import de.danoeh.antennapod.event.playback.SpeedChangedEvent;
import de.danoeh.antennapod.event.settings.SkipIntroEndingChangedEvent;
import de.danoeh.antennapod.event.settings.SpeedPresetChangedEvent;
import de.danoeh.antennapod.event.settings.VolumeAdaptionChangedEvent;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class EventIdentityEqualityTest {

    private void assertReferenceEquality(Object a, Object b) {
        assertNotSame(a, b);
        assertFalse(a.equals(b));
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    private void assertDefaultToStringShape(Object obj) {
        String expected = obj.getClass().getName() + "@" + Integer.toHexString(obj.hashCode());
        assertTrue(obj.toString().equals(expected));
    }

    @Test
    public void streamingConfirmationEventInstancesAreNotEqual() {
        StreamingConfirmationEvent a = new StreamingConfirmationEvent();
        StreamingConfirmationEvent b = new StreamingConfirmationEvent();
        assertReferenceEquality(a, b);
        assertDefaultToStringShape(a);
    }

    @Test
    public void playerStatusEventInstancesAreNotEqual() {
        PlayerStatusEvent a = new PlayerStatusEvent();
        PlayerStatusEvent b = new PlayerStatusEvent();
        assertReferenceEquality(a, b);
        assertDefaultToStringShape(a);
    }

    @Test
    public void discoveryDefaultUpdateEventInstancesAreNotEqual() {
        DiscoveryDefaultUpdateEvent a = new DiscoveryDefaultUpdateEvent();
        DiscoveryDefaultUpdateEvent b = new DiscoveryDefaultUpdateEvent();
        assertReferenceEquality(a, b);
        assertDefaultToStringShape(a);
    }

    @Test
    public void statisticsEventInstancesAreNotEqual() {
        StatisticsEvent a = new StatisticsEvent();
        StatisticsEvent b = new StatisticsEvent();
        assertReferenceEquality(a, b);
        assertDefaultToStringShape(a);
    }

    @Test
    public void playbackPositionEventInstancesAreNotEqual() {
        PlaybackPositionEvent a = new PlaybackPositionEvent(1, 2);
        PlaybackPositionEvent b = new PlaybackPositionEvent(1, 2);
        assertReferenceEquality(a, b);
        assertDefaultToStringShape(a);
    }

    @Test
    public void speedChangedEventInstancesAreNotEqual() {
        SpeedChangedEvent a = new SpeedChangedEvent(1.5f);
        SpeedChangedEvent b = new SpeedChangedEvent(1.5f);
        assertReferenceEquality(a, b);
        assertDefaultToStringShape(a);
    }

    @Test
    public void playerErrorEventInstancesAreNotEqual() {
        PlayerErrorEvent a = new PlayerErrorEvent("msg");
        PlayerErrorEvent b = new PlayerErrorEvent("msg");
        assertReferenceEquality(a, b);
        assertDefaultToStringShape(a);
    }

    @Test
    public void syncServiceEventInstancesAreNotEqual() {
        SyncServiceEvent a = new SyncServiceEvent(1);
        SyncServiceEvent b = new SyncServiceEvent(1);
        assertReferenceEquality(a, b);
        assertDefaultToStringShape(a);
    }

    @Test
    public void volumeAdaptionChangedEventInstancesAreNotEqual() {
        VolumeAdaptionChangedEvent a = new VolumeAdaptionChangedEvent(VolumeAdaptionSetting.OFF, 1L);
        VolumeAdaptionChangedEvent b = new VolumeAdaptionChangedEvent(VolumeAdaptionSetting.OFF, 1L);
        assertReferenceEquality(a, b);
        assertDefaultToStringShape(a);
    }

    @Test
    public void skipIntroEndingChangedEventInstancesAreNotEqual() {
        SkipIntroEndingChangedEvent a = new SkipIntroEndingChangedEvent(1, 2, 3L);
        SkipIntroEndingChangedEvent b = new SkipIntroEndingChangedEvent(1, 2, 3L);
        assertReferenceEquality(a, b);
        assertDefaultToStringShape(a);
    }

    @Test
    public void speedPresetChangedEventInstancesAreNotEqual() {
        SpeedPresetChangedEvent a = new SpeedPresetChangedEvent(1f, 1L, FeedPreferences.SkipSilence.OFF);
        SpeedPresetChangedEvent b = new SpeedPresetChangedEvent(1f, 1L, FeedPreferences.SkipSilence.OFF);
        assertReferenceEquality(a, b);
        assertDefaultToStringShape(a);
    }

    @Test
    public void feedUpdateRunningEventInstancesAreNotEqual() {
        FeedUpdateRunningEvent a = new FeedUpdateRunningEvent(true);
        FeedUpdateRunningEvent b = new FeedUpdateRunningEvent(true);
        assertReferenceEquality(a, b);
        assertDefaultToStringShape(a);
    }

    @Test
    public void playbackServiceEventInstancesAreNotEqual() {
        PlaybackServiceEvent a = new PlaybackServiceEvent(PlaybackServiceEvent.Action.SERVICE_STARTED);
        PlaybackServiceEvent b = new PlaybackServiceEvent(PlaybackServiceEvent.Action.SERVICE_STARTED);
        assertReferenceEquality(a, b);
        assertDefaultToStringShape(a);
    }

    @Test
    public void feedItemEventInstancesAreNotEqual() {
        FeedItemEvent a = new FeedItemEvent(Collections.emptyList(), false);
        FeedItemEvent b = new FeedItemEvent(Collections.emptyList(), false);
        assertReferenceEquality(a, b);
        assertDefaultToStringShape(a);
    }

    @Test
    public void queueEventInstancesAreNotEqual() {
        QueueEvent a = QueueEvent.cleared();
        QueueEvent b = QueueEvent.cleared();
        assertReferenceEquality(a, b);
        assertDefaultToStringShape(a);
    }

    @Test
    public void messageEventInstancesAreNotEqual() {
        MessageEvent a = new MessageEvent("hi");
        MessageEvent b = new MessageEvent("hi");
        assertReferenceEquality(a, b);
        assertDefaultToStringShape(a);
    }

    @Test
    public void bufferUpdateEventInstancesAreNotEqual() {
        BufferUpdateEvent a = BufferUpdateEvent.started();
        BufferUpdateEvent b = BufferUpdateEvent.started();
        assertReferenceEquality(a, b);
        assertDefaultToStringShape(a);
    }

    @Test
    public void sleepTimerUpdatedEventInstancesAreNotEqual() {
        SleepTimerUpdatedEvent a = SleepTimerUpdatedEvent.cancelled();
        SleepTimerUpdatedEvent b = SleepTimerUpdatedEvent.cancelled();
        assertReferenceEquality(a, b);
        assertDefaultToStringShape(a);
    }

    @Test
    public void episodeDownloadEventInstancesAreNotEqual() {
        EpisodeDownloadEvent a = new EpisodeDownloadEvent(new HashMap<>());
        EpisodeDownloadEvent b = new EpisodeDownloadEvent(new HashMap<>());
        assertReferenceEquality(a, b);
        assertDefaultToStringShape(a);
    }

    @Test
    public void feedListUpdateEventInstancesAreNotEqual() {
        FeedListUpdateEvent a = new FeedListUpdateEvent(1L);
        FeedListUpdateEvent b = new FeedListUpdateEvent(1L);
        assertReferenceEquality(a, b);
        assertDefaultToStringShape(a);
    }

    @Test
    public void feedEventInstancesAreNotEqualDespiteCustomToString() {
        FeedEvent a = new FeedEvent(FeedEvent.Action.FILTER_CHANGED, 1L);
        FeedEvent b = new FeedEvent(FeedEvent.Action.FILTER_CHANGED, 1L);
        assertReferenceEquality(a, b);
    }

    @Test
    public void downloadLogEventInstancesAreNotEqualDespiteCustomToString() {
        assertReferenceEquality(DownloadLogEvent.listUpdated(), DownloadLogEvent.listUpdated());
    }

    @Test
    public void playbackHistoryEventInstancesAreNotEqualDespiteCustomToString() {
        assertReferenceEquality(PlaybackHistoryEvent.listUpdated(), PlaybackHistoryEvent.listUpdated());
    }
}
