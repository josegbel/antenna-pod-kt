package de.danoeh.antennapod.model.feed

enum class FeedOrder(@JvmField val id: Int) {
    COUNTER(0),
    ALPHABETICAL(1),
    MOST_PLAYED(3),
    MOST_RECENT_EPISODE(2)
    ;

    companion object {
        @JvmStatic
        fun fromOrdinal(id: Int): FeedOrder {
            for (counter in entries) {
                if (counter.id == id) {
                    return counter
                }
            }
            return MOST_RECENT_EPISODE
        }
    }
}
