package de.danoeh.antennapod.event

import de.danoeh.antennapod.model.feed.FeedItem

class FeedItemEvent(@JvmField val items: List<FeedItem?>, @JvmField val unreadStatusChanged: Boolean) {

    companion object {
        @JvmStatic
        fun indexOfItemWithId(items: List<FeedItem?>, id: Long): Int {
            return items.indexOfFirst { it != null && it.id == id }
        }
    }
}
