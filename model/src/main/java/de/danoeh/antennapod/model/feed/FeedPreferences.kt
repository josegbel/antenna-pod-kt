package de.danoeh.antennapod.model.feed

import java.io.Serializable

class FeedPreferences(
    private var feedID: Long,
    private var autoDownload: AutoDownloadSetting,
    private var keepUpdated: Boolean,
    private var autoDeleteAction: AutoDeleteAction,
    private var volumeAdaptionSetting: VolumeAdaptionSetting,
    private var username: String?,
    private var password: String?,
    private var filter: FeedFilter,
    private var feedPlaybackSpeed: Float,
    private var feedSkipIntro: Int,
    private var feedSkipEnding: Int,
    private var feedSkipSilence: SkipSilence,
    private var showEpisodeNotification: Boolean,
    private var newEpisodesAction: NewEpisodesAction,
    tagsArg: Set<String>
) : Serializable {

    val tags: MutableSet<String> = HashSet()

    init {
        tags.addAll(tagsArg)
    }

    constructor(
        feedID: Long,
        autoDownload: AutoDownloadSetting,
        autoDeleteAction: AutoDeleteAction,
        volumeAdaptionSetting: VolumeAdaptionSetting,
        newEpisodesAction: NewEpisodesAction,
        username: String?,
        password: String?
    ) : this(
        feedID, autoDownload, true, autoDeleteAction, volumeAdaptionSetting, username, password,
        FeedFilter(), SPEED_USE_GLOBAL, 0, 0, SkipSilence.GLOBAL, false, newEpisodesAction, HashSet()
    )

    enum class AutoDeleteAction(@JvmField val code: Int) {
        GLOBAL(0),
        ALWAYS(1),
        NEVER(2);

        companion object {
            @JvmStatic
            fun fromCode(code: Int): AutoDeleteAction {
                for (action in values()) {
                    if (code == action.code) {
                        return action
                    }
                }
                return NEVER
            }
        }
    }

    enum class NewEpisodesAction(@JvmField val code: Int) {
        GLOBAL(0),
        ADD_TO_INBOX(1),
        ADD_TO_QUEUE(3),
        NOTHING(2);

        companion object {
            @JvmStatic
            fun fromCode(code: Int): NewEpisodesAction {
                for (action in values()) {
                    if (code == action.code) {
                        return action
                    }
                }
                return ADD_TO_INBOX
            }
        }
    }

    enum class SkipSilence(@JvmField val code: Int) {
        OFF(0),
        GLOBAL(1),
        AGGRESSIVE(2);

        companion object {
            @JvmStatic
            fun fromCode(code: Int): SkipSilence {
                for (s in values()) {
                    if (s.code == code) {
                        return s
                    }
                }
                return GLOBAL
            }
        }
    }

    enum class AutoDownloadSetting(@JvmField val code: Int) {
        DISABLED(0),
        ENABLED(2),
        GLOBAL(1);

        companion object {
            @JvmStatic
            fun fromInteger(code: Int): AutoDownloadSetting {
                for (setting in values()) {
                    if (code == setting.code) {
                        return setting
                    }
                }
                return GLOBAL
            }

            @JvmStatic
            fun fromBoolean(enabled: Boolean): AutoDownloadSetting {
                return if (enabled) ENABLED else DISABLED
            }
        }
    }

    /**
     * @return the filter for this feed
     */
    fun getFilter(): FeedFilter {
        return filter
    }

    fun setFilter(filter: FeedFilter) {
        this.filter = filter
    }

    /**
     * @return true if this feed should be refreshed when everything else is being refreshed
     *         if false the feed should only be refreshed if requested directly.
     */
    fun getKeepUpdated(): Boolean {
        return keepUpdated
    }

    fun setKeepUpdated(keepUpdated: Boolean) {
        this.keepUpdated = keepUpdated
    }

    /**
     * Update this FeedPreferences object from another one. The feedID, autoDownload and AutoDeleteAction attributes
     * are excluded from the update.
     */
    fun updateFromOther(other: FeedPreferences?) {
        if (other == null) {
            return
        }
        this.username = other.username
        this.password = other.password
    }

    fun getFeedID(): Long {
        return feedID
    }

    fun setFeedID(feedID: Long) {
        this.feedID = feedID
    }

    /**
     * This function returns the calculated auto-download state for the given FeedPreference.
     * By supplying the global default, the returned value will present the actionable state of the
     * download-state choosen by the user. No further checks need to be made.
     * @param globalDefault Global Setting for automatic downloading of items.
     * @return whether this item should be downloaded
     */
    fun isAutoDownload(globalDefault: Boolean): Boolean {
        return when (autoDownload) {
            AutoDownloadSetting.ENABLED -> true
            AutoDownloadSetting.DISABLED -> false
            else -> globalDefault
        }
    }

    /**
     * @return The autodownload settings value for this item.
     */
    fun getAutoDownload(): AutoDownloadSetting {
        return autoDownload
    }

    fun setAutoDownload(setting: AutoDownloadSetting) {
        this.autoDownload = setting
    }

    fun getAutoDeleteAction(): AutoDeleteAction {
        return autoDeleteAction
    }

    fun getVolumeAdaptionSetting(): VolumeAdaptionSetting {
        return volumeAdaptionSetting
    }

    fun getNewEpisodesAction(): NewEpisodesAction {
        return newEpisodesAction
    }

    fun setAutoDeleteAction(autoDeleteAction: AutoDeleteAction) {
        this.autoDeleteAction = autoDeleteAction
    }

    fun setVolumeAdaptionSetting(volumeAdaptionSetting: VolumeAdaptionSetting) {
        this.volumeAdaptionSetting = volumeAdaptionSetting
    }

    fun setNewEpisodesAction(newEpisodesAction: NewEpisodesAction) {
        this.newEpisodesAction = newEpisodesAction
    }

    fun getCurrentAutoDelete(): AutoDeleteAction {
        return autoDeleteAction
    }

    fun getUsername(): String? {
        return username
    }

    fun setUsername(username: String?) {
        this.username = username
    }

    fun getPassword(): String? {
        return password
    }

    fun setPassword(password: String?) {
        this.password = password
    }

    fun getFeedPlaybackSpeed(): Float {
        return feedPlaybackSpeed
    }

    fun setFeedPlaybackSpeed(playbackSpeed: Float) {
        feedPlaybackSpeed = playbackSpeed
    }

    fun setFeedSkipIntro(skipIntro: Int) {
        feedSkipIntro = skipIntro
    }

    fun getFeedSkipIntro(): Int {
        return feedSkipIntro
    }

    fun setFeedSkipEnding(skipEnding: Int) {
        feedSkipEnding = skipEnding
    }

    fun getFeedSkipEnding(): Int {
        return feedSkipEnding
    }

    fun setFeedSkipSilence(skipSilence: SkipSilence) {
        feedSkipSilence = skipSilence
    }

    fun getFeedSkipSilence(): SkipSilence {
        if (feedPlaybackSpeed == SPEED_USE_GLOBAL) {
            return SkipSilence.GLOBAL
        }
        return feedSkipSilence
    }

    fun getTagsAsString(): String {
        return tags.joinToString(TAG_SEPARATOR)
    }

    /**
     * getter for preference if notifications should be display for new episodes.
     * @return true for displaying notifications
     */
    fun getShowEpisodeNotification(): Boolean {
        return showEpisodeNotification
    }

    fun setShowEpisodeNotification(showEpisodeNotification: Boolean) {
        this.showEpisodeNotification = showEpisodeNotification
    }

    companion object {
        private const val serialVersionUID = 1L

        const val SPEED_USE_GLOBAL = -1f
        const val TAG_ROOT = "#root"
        const val TAG_UNTAGGED = "#untagged"
        const val TAG_SEPARATOR = "\u001e"
    }
}
