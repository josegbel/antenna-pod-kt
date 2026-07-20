package de.danoeh.antennapod.model.feed

enum class TranscriptType(
    @JvmField val priority: Int,
    @JvmField val canonicalMime: String
) {
    JSON(4, "application/json"),
    VTT(3, "text/vtt"),
    SRT(2, "application/srt"),
    NONE(0, "")
    ;

    companion object {
        @JvmStatic
        fun fromMime(type: String?): TranscriptType {
            if (type == null) {
                return NONE
            }
            return when (type) {
                "application/json" -> JSON
                "text/vtt" -> VTT
                "application/srt", "application/srr", "application/x-subrip" -> SRT
                else -> NONE
            }
        }
    }
}
