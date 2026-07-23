package de.danoeh.antennapod.model.feed

import java.io.Serializable
import java.util.Date
import java.util.HashSet
import java.util.Objects
import org.apache.commons.lang3.StringUtils

/**
 * Item (episode) within a feed.
 *
 * @author daniel
 */
class FeedItem : Serializable {

    // Constructor assignment (`this.id = id`) routes through this setter in Kotlin, unlike Java's direct
    // field write — currently a no-op only because `media` is always null at construction time in every
    // existing code path. A future constructor change must preserve that ordering or re-derive this
    // reasoning.
    var id: Long = 0
        set(value) {
            field = value
            if (this.media != null) {
                media!!.setItemId(value)
            }
        }

    /**
     * The id/guid that can be found in the rss/atom feed. Might not be set.
     */
    var itemIdentifier: String? = null
    var title: String? = null

    /**
     * The description of a feeditem.
     */
    var description: String? = null
        private set

    var link: String? = null

    // Backing field renamed from Java's `pubDate` to `pubDateField` to allow an explicit
    // getPubDate()/setPubDate() pair over a differently-named backing field (see below). This is safe
    // only because serialization here is intra-session/same-class-version (Bundle/Intent extras within
    // a single app run, never persisted across an app upgrade) — mirroring the serialVersionUID = 1L
    // reasoning above. A cross-version deserialization would not see this field under its old name.
    private var pubDateField: Date? = null

    var media: FeedMedia? = null
        set(value) {
            field = value
            if (value != null && value.getItem() !== this) {
                value.setItem(this)
            }
        }

    @Transient
    var feed: Feed? = null
    var feedId: Long = 0
    var podcastIndexChapterUrl: String? = null
    var socialInteractUrl: String? = null
    private var podcastIndexTranscriptUrl: String? = null
    private var podcastIndexTranscriptType: String? = null
    var transcript: Transcript? = null

    private var state: Int = 0

    var paymentLink: String? = null

    /**
     * Is true if the database contains any chapters that belong to this item. This attribute is only
     * written once by DBReader on initialization.
     * The FeedItem might still have a non-null chapters value. In this case, the list of chapters
     * has not been saved in the database yet.
     */
    @get:JvmName("hasChapters")
    val hasChapters: Boolean

    /**
     * The list of chapters of this item. This might be null even if there are chapters of this item
     * in the database. The 'hasChapters' attribute should be used to check if this item has any chapters.
     */
    @Transient
    var chapters: MutableList<Chapter>? = null
    var imageUrl: String? = null

    var isAutoDownloadEnabled: Boolean = true
        private set

    /**
     * Any tags assigned to this item
     */
    private val tags: MutableSet<String?> = HashSet()

    constructor() {
        this.state = UNPLAYED
        this.hasChapters = false
    }

    /**
     * This constructor is used by DBReader.
     */
    constructor(
        id: Long,
        title: String?,
        link: String?,
        pubDate: Date?,
        paymentLink: String?,
        feedId: Long,
        hasChapters: Boolean,
        imageUrl: String?,
        state: Int,
        itemIdentifier: String?,
        autoDownloadEnabled: Boolean,
        podcastIndexChapterUrl: String?,
        transcriptType: String?,
        transcriptUrl: String?,
        socialInteractUrl: String?
    ) {
        this.id = id
        this.title = title
        this.link = link
        this.pubDateField = pubDate
        this.paymentLink = paymentLink
        this.feedId = feedId
        this.hasChapters = hasChapters
        this.imageUrl = imageUrl
        this.state = state
        this.itemIdentifier = itemIdentifier
        this.isAutoDownloadEnabled = autoDownloadEnabled
        this.podcastIndexChapterUrl = podcastIndexChapterUrl
        this.socialInteractUrl = socialInteractUrl
        if (transcriptUrl != null) {
            this.podcastIndexTranscriptUrl = transcriptUrl
            this.podcastIndexTranscriptType = transcriptType
        }
    }

    /**
     * This constructor should be used for creating test objects.
     */
    constructor(
        id: Long,
        title: String?,
        itemIdentifier: String?,
        link: String?,
        pubDate: Date?,
        state: Int,
        feed: Feed?
    ) {
        this.id = id
        this.title = title
        this.itemIdentifier = itemIdentifier
        this.link = link
        this.pubDateField = if (pubDate != null) pubDate.clone() as Date else null
        this.state = state
        this.feed = feed
        this.hasChapters = false
    }

    /**
     * This constructor should be used for creating test objects involving chapter marks.
     */
    constructor(
        id: Long,
        title: String?,
        itemIdentifier: String?,
        link: String?,
        pubDate: Date?,
        state: Int,
        feed: Feed?,
        hasChapters: Boolean
    ) {
        this.id = id
        this.title = title
        this.itemIdentifier = itemIdentifier
        this.link = link
        this.pubDateField = if (pubDate != null) pubDate.clone() as Date else null
        this.state = state
        this.feed = feed
        this.hasChapters = hasChapters
    }

    fun updateFromOther(other: FeedItem) {
        if (other.imageUrl != null) {
            this.imageUrl = other.imageUrl
        }
        if (other.title != null) {
            title = other.title
        }
        if (other.description != null) {
            description = other.description
        }
        if (other.link != null) {
            link = other.link
        }
        if (other.pubDateField != null && other.pubDateField != pubDateField) {
            pubDateField = other.pubDateField
        }
        if (other.media != null) {
            if (media == null) {
                media = other.media
                // reset to new if feed item did link to a file before
                setNew()
            } else if (media!!.compareWithOther(other.media)) {
                media!!.updateFromOther(other.media)
            }
        }
        if (other.paymentLink != null) {
            paymentLink = other.paymentLink
        }
        if (other.chapters != null) {
            if (!hasChapters) {
                chapters = other.chapters
            }
        }
        if (other.podcastIndexChapterUrl != null) {
            podcastIndexChapterUrl = other.podcastIndexChapterUrl
        }
        if (other.socialInteractUrl != null) {
            socialInteractUrl = other.socialInteractUrl
        }
        if (other.getTranscriptUrl() != null) {
            podcastIndexTranscriptUrl = other.podcastIndexTranscriptUrl
        }
        if (other.getTranscriptType() != null) {
            podcastIndexTranscriptType = other.podcastIndexTranscriptType
        }
    }

    /**
     * Returns the value that uniquely identifies this FeedItem. If the
     * itemIdentifier attribute is not null, it will be returned. Else it will
     * try to return the title. If the title is not given, it will use the link
     * of the entry.
     */
    fun getIdentifyingValue(): String? {
        return if (itemIdentifier != null && itemIdentifier!!.isNotEmpty()) {
            itemIdentifier
        } else if (title != null && title!!.isNotEmpty()) {
            title
        } else if (hasMedia() && media!!.getDownloadUrl() != null) {
            media!!.getDownloadUrl()
        } else {
            link
        }
    }

    /**
     * Get the link for the feed item for the purpose of Share.
     * It falls backs to the feed's link if the item has no link.
     */
    fun getLinkWithFallback(): String? {
        if (StringUtils.isNotBlank(link)) {
            return link
        } else if (StringUtils.isNotBlank(feed!!.link)) {
            return feed!!.link
        }
        return null
    }

    fun getPubDate(): Date? {
        return if (pubDateField != null) pubDateField!!.clone() as Date else null
    }

    fun setPubDate(pubDate: Date?) {
        this.pubDateField = if (pubDate != null) pubDate.clone() as Date else null
    }

    val isNew: Boolean
        get() = state == NEW

    fun getPlayState(): Int {
        return state
    }

    fun setPlayState(state: Int) {
        this.state = state
    }

    fun setNew() {
        state = NEW
    }

    var isPlayed: Boolean
        get() = state == PLAYED
        set(played) {
            state = if (played) PLAYED else UNPLAYED
        }

    val isInProgress: Boolean
        get() = media != null && media!!.isInProgress()

    /**
     * Updates this item's description property if the given argument is longer than the already stored description
     * @param newDescription The new item description, content:encoded, itunes:description, etc.
     */
    fun setDescriptionIfLonger(newDescription: String?) {
        if (newDescription == null) {
            return
        }
        if (this.description == null) {
            this.description = newDescription
        } else if (this.description!!.length < newDescription.length) {
            this.description = newDescription
        }
    }

    fun hasMedia(): Boolean {
        return media != null
    }

    /**
     * Returns the image of this item, as specified in the feed.
     * To load the image that can be displayed to the user, use [.getImageLocation],
     * which also considers embedded pictures or the feed picture if no other picture is present.
     */
    fun getImageLocation(): String? {
        return if (imageUrl != null) {
            imageUrl
        } else if (media != null && media!!.hasEmbeddedPicture()) {
            FeedMedia.FILENAME_PREFIX_EMBEDDED_COVER + media!!.getLocalFileUrl()
        } else if (feed != null) {
            feed!!.imageUrl
        } else {
            null
        }
    }

    fun disableAutoDownload() {
        this.isAutoDownloadEnabled = false
    }

    val isDownloaded: Boolean
        get() = media != null && media!!.isDownloaded()

    /**
     * @return true if the item has this tag
     */
    fun isTagged(tag: String?): Boolean {
        return tags.contains(tag)
    }

    /**
     * @param tag adds this tag to the item. NOTE: does NOT persist to the database
     */
    fun addTag(tag: String?) {
        tags.add(tag)
    }

    /**
     * @param tag the to remove
     */
    fun removeTag(tag: String?) {
        tags.remove(tag)
    }

    fun setTranscriptUrl(type: String?, url: String?) {
        updateTranscriptPreferredFormat(type, url)
    }

    fun getTranscriptUrl(): String? {
        return podcastIndexTranscriptUrl
    }

    fun getTranscriptType(): String? {
        return podcastIndexTranscriptType
    }

    fun updateTranscriptPreferredFormat(typeStr: String?, url: String?) {
        if (StringUtils.isEmpty(typeStr) || StringUtils.isEmpty(url)) {
            return
        }
        val type = TranscriptType.fromMime(typeStr)
        val previousType = TranscriptType.fromMime(podcastIndexTranscriptType)
        if (type.priority > previousType.priority) {
            podcastIndexTranscriptUrl = url
            podcastIndexTranscriptType = type.canonicalMime
        }
    }

    fun hasTranscript(): Boolean {
        return podcastIndexTranscriptUrl != null
    }

    override fun toString(): String {
        return "FeedItem [id=$id, title=$title, feedId=$feedId, pubDate=$pubDateField]"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }

        val feedItem = other as FeedItem
        return id == feedItem.id
    }

    override fun hashCode(): Int {
        return Objects.hash(id)
    }

    companion object {
        private const val serialVersionUID = 1L

        /** tag that indicates this item is in the queue */
        const val TAG_QUEUE = "Queue"

        /** tag that indicates this item is in favorites */
        const val TAG_FAVORITE = "Favorite"

        const val NEW = -1
        const val UNPLAYED = 0
        const val PLAYED = 1
    }
}
