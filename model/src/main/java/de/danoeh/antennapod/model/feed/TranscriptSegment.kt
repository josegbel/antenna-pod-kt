package de.danoeh.antennapod.model.feed

class TranscriptSegment(
    private val startTime: Long,
    private var endTime: Long,
    private var words: String?,
    private val speaker: String?
) {

    fun append(newEndTime: Long, wordsToAppend: String?) {
        endTime = newEndTime
        words += " " + wordsToAppend
    }

    fun getStartTime(): Long {
        return startTime
    }

    fun getEndTime(): Long {
        return endTime
    }

    fun getWords(): String? {
        return words
    }

    fun getSpeaker(): String? {
        return speaker
    }
}
