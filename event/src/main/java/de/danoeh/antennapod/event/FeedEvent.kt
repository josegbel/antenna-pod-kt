package de.danoeh.antennapod.event

class FeedEvent(private val action: Action?, @JvmField val feedId: Long) {

    enum class Action {
        FILTER_CHANGED,
        SORT_ORDER_CHANGED
    }

    override fun toString(): String {
        return "FeedEvent{action=$action, feedId=$feedId}"
    }
}
