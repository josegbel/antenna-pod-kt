package de.danoeh.antennapod.event.settings;

import org.junit.Test;

import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class VolumeAdaptionChangedEventTest {

    @Test
    public void volumeAdaptionSettingAndFeedIdAreStored() {
        VolumeAdaptionChangedEvent event =
                new VolumeAdaptionChangedEvent(VolumeAdaptionSetting.LIGHT_REDUCTION, 3L);
        assertSame(VolumeAdaptionSetting.LIGHT_REDUCTION, event.getVolumeAdaptionSetting());
        assertEquals(3L, event.getFeedId());
    }
}
