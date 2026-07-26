package de.danoeh.antennapod.event

import de.danoeh.antennapod.model.feed.Feed

class FeedListUpdateEvent(feeds: List<Feed>) {

    private val feeds: MutableList<Long> = ArrayList()

    init {
        for (feed in feeds) {
            this.feeds.add(feed.id)
        }
    }

    constructor(feed: Feed) : this(listOf(feed))

    constructor(feedId: Long) : this(emptyList<Feed>()) {
        feeds.add(feedId)
    }

    fun contains(feed: Feed): Boolean {
        return feeds.contains(feed.id)
    }
}
