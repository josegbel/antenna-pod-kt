package de.danoeh.antennapod.model.feed

import java.util.Objects

class Chapter {
    var id: Long = 0

    /** The start time of the chapter in milliseconds */
    var start: Long = 0
    var title: String? = null
    var link: String? = null
    var imageUrl: String? = null

    /**
     * ID from the chapter source, not the database ID.
     */
    var chapterId: String? = null

    constructor()

    constructor(start: Long, title: String?, link: String?, imageUrl: String?) {
        this.start = start
        this.title = title
        this.link = link
        this.imageUrl = imageUrl
    }

    override fun toString(): String {
        return "Chapter [title=$title, start=$start, url=$link]"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }

        val chapter = other as Chapter
        return id == chapter.id
    }

    override fun hashCode(): Int {
        return Objects.hash(id)
    }

    companion object {
        @JvmStatic
        fun getAfterPosition(chapters: List<Chapter>?, playbackPosition: Int): Int {
            if (chapters == null || chapters.isEmpty()) return -1
            for (i in chapters.indices) {
                if (chapters[i].start > playbackPosition) return i - 1
            }
            return chapters.size - 1
        }
    }
}
