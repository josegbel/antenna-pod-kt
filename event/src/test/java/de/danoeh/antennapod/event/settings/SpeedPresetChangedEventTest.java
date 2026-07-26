package de.danoeh.antennapod.event.settings;

import org.junit.Test;

import de.danoeh.antennapod.model.feed.FeedPreferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class SpeedPresetChangedEventTest {

    @Test
    public void speedFeedIdAndSkipSilenceAreStored() {
        SpeedPresetChangedEvent event =
                new SpeedPresetChangedEvent(1.25f, 11L, FeedPreferences.SkipSilence.AGGRESSIVE);
        assertEquals(1.25f, event.getSpeed(), 0f);
        assertEquals(11L, event.getFeedId());
        assertSame(FeedPreferences.SkipSilence.AGGRESSIVE, event.getSkipSilence());
    }
}
