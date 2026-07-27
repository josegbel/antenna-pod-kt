package de.danoeh.antennapod.event.settings

import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class VolumeAdaptionChangedEventTest {

    @Test
    fun volumeAdaptionSettingAndFeedIdAreStored() {
        val event =
            VolumeAdaptionChangedEvent(VolumeAdaptionSetting.LIGHT_REDUCTION, 3L)
        assertSame(VolumeAdaptionSetting.LIGHT_REDUCTION, event.volumeAdaptionSetting)
        assertEquals(3L, event.feedId)
    }
}
