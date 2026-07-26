package de.danoeh.antennapod.event.settings

import de.danoeh.antennapod.model.feed.FeedPreferences

class SpeedPresetChangedEvent(val speed: Float, val feedId: Long, val skipSilence: FeedPreferences.SkipSilence?)
