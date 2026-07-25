package de.danoeh.antennapod.model.feed

import de.danoeh.antennapod.model.playback.Playable
import java.util.regex.Pattern

class EmbeddedChapterImage(val media: Playable?, private val imageUrl: String) {

    val position: Int
    val length: Int

    init {
        val m = EMBEDDED_IMAGE_MATCHER.matcher(imageUrl)
        if (m.find()) {
            position = m.group(1)!!.toInt()
            length = m.group(2)!!.toInt()
        } else {
            throw IllegalArgumentException("Not an embedded chapter")
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        val that = other as EmbeddedChapterImage
        return imageUrl == that.imageUrl
    }

    override fun hashCode(): Int {
        return imageUrl.hashCode()
    }

    companion object {
        private val EMBEDDED_IMAGE_MATCHER = Pattern.compile("embedded-image://(\\d+)/(\\d+)")

        @JvmStatic
        fun makeUrl(position: Int, length: Int): String {
            return "embedded-image://$position/$length"
        }

        private fun isEmbeddedChapterImage(imageUrl: String?): Boolean {
            return EMBEDDED_IMAGE_MATCHER.matcher(imageUrl).matches()
        }

        @JvmStatic
        fun getModelFor(media: Playable, chapter: Int): Any {
            // media.chapters is now a Kotlin-nullable property (Playable.chapters: List<Chapter>?).
            // A null chapters list still throws NullPointerException here (preserving the
            // crash-on-null behavior a caller with an unguarded null would previously see from
            // Playable.getChapters()), but the message regresses from a detailed JEP-358
            // diagnostic to none - there is no Java platform-type boundary left downstream to
            // launder the null through and recover it. Do not swallow this with `?.`, which
            // would silently return null instead of throwing.
            val imageUrl = media.chapters!![chapter].imageUrl
            return if (isEmbeddedChapterImage(imageUrl)) {
                EmbeddedChapterImage(media, imageUrl!!)
            } else {
                imageUrl!!
            }
        }
    }
}
