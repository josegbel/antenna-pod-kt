package de.danoeh.antennapod.model.playback

enum class MediaType {
    AUDIO,
    VIDEO,
    UNKNOWN
    ;

    companion object {
        private val AUDIO_APPLICATION_MIME_STRINGS = setOf(
            "application/ogg",
            "application/opus",
            "application/x-flac"
        )

        @JvmStatic
        fun fromMimeType(mimeType: String?): MediaType {
            return when {
                mimeType.isNullOrEmpty() -> UNKNOWN
                mimeType.startsWith("audio") -> AUDIO
                mimeType.startsWith("video") -> VIDEO
                mimeType in AUDIO_APPLICATION_MIME_STRINGS -> AUDIO
                else -> UNKNOWN
            }
        }
    }
}
