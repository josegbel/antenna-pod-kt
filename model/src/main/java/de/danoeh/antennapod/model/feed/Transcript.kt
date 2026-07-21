package de.danoeh.antennapod.model.feed

class Transcript {
    private var speakers: Set<String>? = null
    private val segments = ArrayList<TranscriptSegment>()

    fun addSegment(segment: TranscriptSegment) {
        if (segments.isNotEmpty() && segments[segments.size - 1].getStartTime() >= segment.getStartTime()) {
            throw IllegalArgumentException("Segments must be added in sorted order")
        }
        segments.add(segment)
    }

    fun findSegmentIndexBefore(time: Long): Int {
        var a = 0
        var b = segments.size - 1
        while (a < b) {
            val pivot = (a + b + 1) / 2
            if (segments[pivot].getStartTime() > time) {
                b = pivot - 1
            } else {
                a = pivot
            }
        }
        return a
    }

    fun getSegmentAt(index: Int): TranscriptSegment {
        return segments[index]
    }

    fun getSegmentAtTime(time: Long): TranscriptSegment {
        return getSegmentAt(findSegmentIndexBefore(time))
    }

    fun getSpeakers(): Set<String>? {
        return speakers
    }

    fun setSpeakers(speakers: Set<String>?) {
        this.speakers = speakers
    }

    fun getSegmentCount(): Int {
        return segments.size
    }
}
