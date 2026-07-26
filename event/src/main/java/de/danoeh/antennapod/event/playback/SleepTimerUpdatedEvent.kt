package de.danoeh.antennapod.event.playback

import de.danoeh.antennapod.model.playback.TimerValue
import kotlin.math.abs
import kotlin.math.max

class SleepTimerUpdatedEvent private constructor(private val timerValue: TimerValue) {

    fun getMillisTimeLeft(): Long {
        return abs(timerValue.getMillisValue())
    }

    fun getDisplayTimeLeft(): Long {
        return abs(timerValue.getDisplayValue())
    }

    fun isOver(): Boolean {
        return timerValue.getMillisValue() == 0L
    }

    fun wasJustEnabled(): Boolean {
        return timerValue.getMillisValue() < 0
    }

    fun isCancelled(): Boolean {
        return timerValue.getMillisValue() == CANCELLED
    }

    companion object {
        private const val CANCELLED = Long.MAX_VALUE

        @JvmStatic
        fun justEnabled(timer: TimerValue): SleepTimerUpdatedEvent {
            return SleepTimerUpdatedEvent(TimerValue(timer.getDisplayValue(), -timer.getMillisValue()))
        }

        @JvmStatic
        fun updated(timer: TimerValue): SleepTimerUpdatedEvent {
            return SleepTimerUpdatedEvent(
                TimerValue(max(timer.getDisplayValue(), 0L), max(0L, timer.getMillisValue()))
            )
        }

        @JvmStatic
        fun cancelled(): SleepTimerUpdatedEvent {
            return SleepTimerUpdatedEvent(TimerValue(CANCELLED, CANCELLED))
        }
    }
}
