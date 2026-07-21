package de.danoeh.antennapod.model.feed

import java.io.Serializable

class FeedItemFilter : Serializable {

    private val properties: Array<String>

    @JvmField val showPlayed: Boolean

    @JvmField val showUnplayed: Boolean

    @JvmField val showPaused: Boolean

    @JvmField val showNotPaused: Boolean

    @JvmField val showNew: Boolean

    @JvmField val showQueued: Boolean

    @JvmField val showNotQueued: Boolean

    @JvmField val showDownloaded: Boolean

    @JvmField val showNotDownloaded: Boolean

    @JvmField val showHasMedia: Boolean

    @JvmField val showNoMedia: Boolean

    @JvmField val showIsFavorite: Boolean

    @JvmField val showNotFavorite: Boolean

    @JvmField val showInHistory: Boolean

    @JvmField val includeSubscribed: Boolean

    @JvmField val includeArchived: Boolean

    @JvmField val includeNotSubscribed: Boolean

    constructor(properties: String) : this(*splitProperties(properties))

    constructor(filter: FeedItemFilter, vararg additionalProperties: String) :
        this(filter.getValues().joinToString(",") + "," + additionalProperties.joinToString(","))

    constructor(vararg properties: String) {
        val joined = properties.joinToString(",")
        this.properties = splitProperties(joined)

        // see R.arrays.feed_filter_values
        showUnplayed = hasProperty(UNPLAYED)
        showPaused = hasProperty(PAUSED)
        showNotPaused = hasProperty(NOT_PAUSED)
        showPlayed = hasProperty(PLAYED)
        showQueued = hasProperty(QUEUED)
        showNotQueued = hasProperty(NOT_QUEUED)
        showDownloaded = hasProperty(DOWNLOADED)
        showNotDownloaded = hasProperty(NOT_DOWNLOADED)
        showHasMedia = hasProperty(HAS_MEDIA)
        showNoMedia = hasProperty(NO_MEDIA)
        showIsFavorite = hasProperty(IS_FAVORITE)
        showNotFavorite = hasProperty(NOT_FAVORITE)
        showNew = hasProperty(NEW)
        showInHistory = hasProperty(IS_IN_HISTORY)
        includeSubscribed = hasProperty(INCLUDE_SUBSCRIBED)
        includeArchived = hasProperty(INCLUDE_ARCHIVED)
        includeNotSubscribed = hasProperty(INCLUDE_NOT_SUBSCRIBED)
    }

    private fun hasProperty(property: String): Boolean {
        return properties.asList().contains(property)
    }

    fun getValues(): Array<String> {
        return properties.clone()
    }

    fun getValuesList(): List<String> {
        return properties.asList()
    }

    fun without(property: String): FeedItemFilter {
        val newValues = ArrayList(properties.asList())
        newValues.remove(property)
        return FeedItemFilter(*newValues.toTypedArray())
    }

    fun matches(item: FeedItem): Boolean {
        if (showNew && !item.isNew) {
            return false
        } else if (showPlayed && !item.isPlayed) {
            return false
        } else if (showUnplayed && item.isPlayed) {
            return false
        } else if (showPaused && !item.isInProgress) {
            return false
        } else if (showNotPaused && item.isInProgress) {
            return false
        } else if (showNew && !item.isNew) {
            return false
        } else if (showQueued && !item.isTagged(FeedItem.TAG_QUEUE)) {
            return false
        } else if (showNotQueued && item.isTagged(FeedItem.TAG_QUEUE)) {
            return false
        } else if (showDownloaded && !item.isDownloaded) {
            return false
        } else if (showNotDownloaded && item.isDownloaded) {
            return false
        } else if (showHasMedia && !item.hasMedia()) {
            return false
        } else if (showNoMedia && item.hasMedia()) {
            return false
        } else if (showIsFavorite && !item.isTagged(FeedItem.TAG_FAVORITE)) {
            return false
        } else if (showNotFavorite && item.isTagged(FeedItem.TAG_FAVORITE)) {
            return false
        } else if (showInHistory &&
            item.media != null && item.media!!.getLastPlayedTimeHistory().time == 0L
        ) {
            return false
        }
        if (item.feed != null) {
            val state = item.feed!!.getState()
            if (includeSubscribed || includeArchived || includeNotSubscribed) {
                if (state == Feed.STATE_SUBSCRIBED && !includeSubscribed) {
                    return false
                } else if (state == Feed.STATE_ARCHIVED && !includeArchived) {
                    return false
                } else if (state == Feed.STATE_NOT_SUBSCRIBED && !includeNotSubscribed) {
                    return false
                }
            } else if (state != Feed.STATE_SUBSCRIBED) {
                return false
            }
        }
        return true
    }

    companion object {
        private const val serialVersionUID = 1L

        const val PLAYED = "played"
        const val UNPLAYED = "unplayed"
        const val NEW = "new"
        const val PAUSED = "paused"
        const val NOT_PAUSED = "not_paused"
        const val IS_FAVORITE = "is_favorite"
        const val NOT_FAVORITE = "not_favorite"
        const val HAS_MEDIA = "has_media"
        const val NO_MEDIA = "no_media"
        const val QUEUED = "queued"
        const val NOT_QUEUED = "not_queued"
        const val DOWNLOADED = "downloaded"
        const val NOT_DOWNLOADED = "not_downloaded"
        const val IS_IN_HISTORY = "is_in_history"
        const val INCLUDE_SUBSCRIBED = "include_subscribed"
        const val INCLUDE_ARCHIVED = "include_archived"
        const val INCLUDE_NOT_SUBSCRIBED = "include_not_subscribed"
        const val INCLUDE_ALL_FEED_STATES = "$INCLUDE_SUBSCRIBED,$INCLUDE_ARCHIVED,$INCLUDE_NOT_SUBSCRIBED"

        @JvmStatic
        fun unfiltered(): FeedItemFilter {
            return FeedItemFilter()
        }

        private fun splitProperties(joined: String): Array<String> {
            return if (joined.isEmpty()) arrayOf() else (joined as java.lang.String).split(",")
        }
    }
}
