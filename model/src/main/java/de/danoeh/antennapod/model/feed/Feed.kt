package de.danoeh.antennapod.model.feed

import java.io.Serializable
import java.util.ArrayList
import java.util.Date
import java.util.Objects
import org.apache.commons.lang3.StringUtils

/**
 * Data Object for a whole feed.
 *
 * @author daniel
 */
class Feed : Serializable {

    // Constructor assignment (`this.id = id`) routes through this setter in Kotlin, unlike Java's direct
    // field write — currently a no-op only because `preferences` is always null at construction time in
    // every existing code path (see FeedCursor.java, which attaches preferences after construction). A
    // future constructor change must preserve that ordering or re-derive this reasoning.
    var id: Long = 0
        set(value) {
            field = value
            preferences?.setFeedID(value)
        }

    var localFileUrl: String? = null
    var downloadUrl: String? = null

    /**
     * title as defined by the feed.
     */
    var feedTitle: String? = null
        private set

    private var customTitleValue: String? = null

    /**
     * Contains 'id'-element in Atom feed.
     */
    var feedIdentifier: String? = null

    /**
     * Link to the website.
     */
    var link: String? = null
    var description: String? = null
    var language: String? = null

    /**
     * Name of the author.
     */
    var author: String? = null
    var imageUrl: String? = null
    var items: MutableList<FeedItem>? = null

    /**
     * String that identifies the last update (adopted from Last-Modified or ETag header).
     */
    var lastModified: String? = null
    var lastRefreshAttempt: Long = 0

    private var fundingList: ArrayList<FeedFunding>? = null

    /**
     * Feed type, for example RSS 2 or Atom.
     */
    var type: String? = null

    /**
     * Feed preferences.
     */
    var preferences: FeedPreferences? = null

    /**
     * The page number that this feed is on. Only feeds with page number "0" should be stored in the
     * database, feed objects with a higher page number only exist temporarily and should be merged
     * into feeds with page number "0".
     *
     * This attribute's value is not saved in the database
     */
    var pageNr: Int = 0

    /**
     * True if this is a "paged feed", i.e. there exist other feed files that belong to the same
     * logical feed.
     */
    var isPaged: Boolean = false

    /**
     * Link to the next page of this feed. If this feed object represents a logical feed (i.e. a feed
     * that is saved in the database) this might be null while still being a paged feed.
     */
    var nextPageLink: String? = null

    @get:JvmName("hasLastUpdateFailed")
    var lastUpdateFailed: Boolean = false

    /**
     * Contains property strings. If such a property applies to a feed item, it is not shown in the feed list
     */
    var itemFilter: FeedItemFilter? = null
        private set

    /**
     * User-preferred sortOrder for display.
     * Only those of scope [SortOrder.Scope.INTRA_FEED] is allowed.
     */
    var sortOrder: SortOrder? = null
        set(value) {
            if (value != null && value.scope != SortOrder.Scope.INTRA_FEED) {
                throw IllegalArgumentException(
                    "The specified sortOrder $value is invalid. Only those with INTRA_FEED scope are allowed."
                )
            }
            field = value
        }

    private var state: Int = 0

    /**
     * This constructor is used for restoring a feed from the database.
     */
    constructor(
        id: Long,
        lastModified: String?,
        title: String?,
        customTitle: String?,
        link: String?,
        description: String?,
        paymentLinks: String?,
        author: String?,
        language: String?,
        type: String?,
        feedIdentifier: String?,
        imageUrl: String?,
        fileUrl: String?,
        downloadUrl: String?,
        lastRefreshAttempt: Long,
        paged: Boolean,
        nextPageLink: String?,
        filter: String?,
        sortOrder: SortOrder?,
        lastUpdateFailed: Boolean,
        state: Int
    ) {
        this.localFileUrl = fileUrl
        this.downloadUrl = downloadUrl
        this.lastRefreshAttempt = lastRefreshAttempt
        this.id = id
        this.feedTitle = title
        this.customTitleValue = customTitle
        this.lastModified = lastModified
        this.link = link
        this.description = description
        this.fundingList = FeedFunding.extractPaymentLinks(paymentLinks)
        this.author = author
        this.language = language
        this.type = type
        this.feedIdentifier = feedIdentifier
        this.imageUrl = imageUrl
        this.isPaged = paged
        this.nextPageLink = nextPageLink
        this.items = ArrayList()
        this.itemFilter = if (filter != null) FeedItemFilter(filter) else FeedItemFilter()
        this.sortOrder = sortOrder
        this.lastUpdateFailed = lastUpdateFailed
        this.state = state
    }

    /**
     * This constructor is used for test purposes.
     */
    constructor(
        id: Long,
        lastModified: String?,
        title: String?,
        link: String?,
        description: String?,
        paymentLink: String?,
        author: String?,
        language: String?,
        type: String?,
        feedIdentifier: String?,
        imageUrl: String?,
        fileUrl: String?,
        downloadUrl: String?,
        lastRefreshAttempt: Long
    ) : this(
        id, lastModified, title, null, link, description, paymentLink, author, language, type,
        feedIdentifier, imageUrl, fileUrl, downloadUrl, lastRefreshAttempt, false, null, null, null,
        false, STATE_SUBSCRIBED
    )

    /**
     * This constructor is used for requesting a feed download (it must not be used for anything else!).
     * It should NOT be used if the title of the feed is already known.
     */
    constructor(url: String?, lastModified: String?) {
        this.localFileUrl = null
        this.downloadUrl = url
        this.lastRefreshAttempt = 0
        this.lastModified = lastModified
    }

    /**
     * This constructor is used for requesting a feed download (it must not be used for anything else!). It should be
     * used if the title of the feed is already known.
     */
    constructor(url: String?, lastModified: String?, title: String?) : this(url, lastModified) {
        this.feedTitle = title
    }

    /**
     * This constructor is used for requesting a feed download (it must not be used for anything else!). It should be
     * used if the title of the feed is already known.
     */
    constructor(url: String?, lastModified: String?, title: String?, username: String?, password: String?) :
        this(url, lastModified, title) {
        preferences = FeedPreferences(
            0, FeedPreferences.AutoDownloadSetting.GLOBAL,
            FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF,
            FeedPreferences.NewEpisodesAction.GLOBAL, username, password
        )
    }

    /**
     * Returns the item at the specified index.
     */
    fun getItemAtIndex(position: Int): FeedItem {
        return items!![position]
    }

    /**
     * Returns the value that uniquely identifies this Feed. If the
     * feedIdentifier attribute is not null, it will be returned. Else it will
     * try to return the title. If the title is not given, it will use the link
     * of the feed.
     */
    fun getIdentifyingValue(): String? {
        return if (feedIdentifier != null && feedIdentifier!!.isNotEmpty()) {
            feedIdentifier
        } else if (downloadUrl != null && downloadUrl!!.isNotEmpty()) {
            downloadUrl
        } else if (feedTitle != null && feedTitle!!.isNotEmpty()) {
            feedTitle
        } else {
            link
        }
    }

    fun getHumanReadableIdentifier(): String? {
        return if (!StringUtils.isEmpty(customTitleValue)) {
            customTitleValue
        } else if (!StringUtils.isEmpty(feedTitle)) {
            feedTitle
        } else {
            downloadUrl
        }
    }

    fun updateFromOther(other: Feed) {
        // don't update feed's download_url, we do that manually if redirected
        // see AntennapodHttpClient
        if (other.imageUrl != null) {
            this.imageUrl = other.imageUrl
        }
        if (other.feedTitle != null) {
            feedTitle = other.feedTitle
        }
        if (other.feedIdentifier != null) {
            feedIdentifier = other.feedIdentifier
        }
        if (other.link != null) {
            link = other.link
        }
        if (other.description != null) {
            description = other.description
        }
        if (other.language != null) {
            language = other.language
        }
        if (other.author != null) {
            author = other.author
        }
        if (other.fundingList != null) {
            fundingList = other.fundingList
        }
        if (other.lastRefreshAttempt > lastRefreshAttempt) {
            lastRefreshAttempt = other.lastRefreshAttempt
        }
        // this feed's nextPage might already point to a higher page, so we only update the nextPage value
        // if this feed is not paged and the other feed is.
        if (!this.isPaged && other.isPaged) {
            this.isPaged = other.isPaged
            this.nextPageLink = other.nextPageLink
        }
    }

    fun getMostRecentItem(): FeedItem? {
        // we could sort, but we don't need to, a simple search is fine...
        var mostRecentDate = Date(0)
        var mostRecentItem: FeedItem? = null
        for (item in items!!) {
            if (item.getPubDate() != null && item.getPubDate()!!.after(mostRecentDate)) {
                mostRecentDate = item.getPubDate()!!
                mostRecentItem = item
            }
        }
        return mostRecentItem
    }

    var title: String?
        get() = if (!StringUtils.isEmpty(customTitleValue)) customTitleValue else feedTitle
        set(value) {
            this.feedTitle = value
        }

    fun getCustomTitle(): String? {
        return this.customTitleValue
    }

    fun setCustomTitle(customTitle: String?) {
        this.customTitleValue = if (customTitle == null || customTitle == feedTitle) null else customTitle
    }

    @Suppress("UNCHECKED_CAST")
    fun addPayment(funding: FeedFunding?) {
        if (fundingList == null) {
            fundingList = ArrayList()
        }
        // fundingList's declared element type stays FeedFunding (non-null) to preserve the public
        // getPaymentLinks(): ArrayList<FeedFunding>? API exactly; this cast mirrors the Java original's
        // ArrayList<FeedFunding>.add(null), which succeeds silently due to generic type erasure.
        (fundingList as ArrayList<FeedFunding?>).add(funding)
    }

    fun getPaymentLinks(): ArrayList<FeedFunding>? {
        return fundingList
    }

    fun hasEpisodeInApp(): Boolean {
        val currentItems = items ?: return false
        for (item in currentItems) {
            if (item.isTagged(FeedItem.TAG_FAVORITE) || item.isTagged(FeedItem.TAG_QUEUE) || item.isDownloaded) {
                return true
            }
        }
        return false
    }

    fun hasInteractedWithEpisode(): Boolean {
        val currentItems = items ?: return false
        for (item in currentItems) {
            if (item.isTagged(FeedItem.TAG_FAVORITE) || item.isTagged(FeedItem.TAG_QUEUE) ||
                item.isDownloaded || item.isPlayed
            ) {
                return true
            }
            if (item.media != null && item.media!!.position > 0) {
                return true
            }
        }
        return false
    }

    fun isLocalFeed(): Boolean {
        return downloadUrl!!.startsWith(PREFIX_LOCAL_FOLDER)
    }

    fun getState(): Int {
        return state
    }

    fun setState(state: Int) {
        this.state = state
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }

        val feed = other as Feed
        return id == feed.id
    }

    override fun hashCode(): Int {
        return Objects.hash(id)
    }

    companion object {
        private const val serialVersionUID = 1L

        const val FEEDFILETYPE_FEED = 0
        const val STATE_SUBSCRIBED = 0
        const val STATE_NOT_SUBSCRIBED = 1
        const val STATE_ARCHIVED = 2
        const val TYPE_RSS2 = "rss"
        const val TYPE_ATOM1 = "atom"
        const val PREFIX_LOCAL_FOLDER = "antennapod_local:"
        const val PREFIX_GENERATIVE_COVER = "antennapod_generative_cover:"
    }
}
