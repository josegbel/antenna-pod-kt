package de.danoeh.antennapod.model.playback

class TimerValue(private val displayValue: Long, private val millisValue: Long) {

    // Value shown to user (milliseconds or number of episodes)
    fun getDisplayValue(): Long {
        return displayValue
    }

    fun getMillisValue(): Long {
        return millisValue
    }
}
