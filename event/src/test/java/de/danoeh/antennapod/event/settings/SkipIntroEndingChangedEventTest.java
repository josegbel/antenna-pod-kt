package de.danoeh.antennapod.event.settings;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SkipIntroEndingChangedEventTest {

    @Test
    public void skipIntroSkipEndingAndFeedIdAreStored() {
        SkipIntroEndingChangedEvent event = new SkipIntroEndingChangedEvent(5, 10, 7L);
        assertEquals(5, event.getSkipIntro());
        assertEquals(10, event.getSkipEnding());
        assertEquals(7L, event.getFeedId());
    }
}
