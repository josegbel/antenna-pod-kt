package de.danoeh.antennapod.model.playback

import android.os.Parcel
import android.os.Parcelable
import de.danoeh.antennapod.model.feed.Chapter
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import java.util.Date
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.builder.HashCodeBuilder

/**
 * Playable implementation for media for which a local version of
 * [FeedMedia] hasn't been found.
 * Used for Casting and for previewing unsubscribed feeds.
 */
class RemoteMedia(
    val downloadUrl: String?,
    private val itemIdentifier: String?,
    val feedUrl: String?,
    override val feedTitle: String?,
    override val episodeTitle: String?,
    val episodeLink: String?,
    val feedAuthor: String?,
    val imageUrl: String?,
    val feedLink: String?,
    val mimeType: String?,
    override val pubDate: Date?,
    val notes: String?
) : Playable {

    override var chapters: List<Chapter>? = null
    override var duration: Int = 0
    override var position: Int = 0
    override var lastPlayedTimeStatistics: Long = 0

    constructor(item: FeedItem) : this(
        // item.media/item.feed are unconditionally dereferenced here, matching the original
        // Java's unguarded chains (item.getMedia().getDownloadUrl(), item.getFeed()....) -
        // preserving the crash-on-null behavior. This constructor has exactly one caller
        // repo-wide (a test helper), so the risk is not production-reachable, but the null
        // check is real: this is a fourth, plan-unanticipated `!!` beyond the two disclosed
        // Playable-nullability sites (see Implementation Notes / Deviations from plan).
        item.media!!.downloadUrl,
        item.itemIdentifier,
        item.feed!!.downloadUrl,
        item.feed!!.title,
        item.title,
        item.link,
        item.feed!!.author,
        if (!StringUtils.isEmpty(item.imageUrl)) item.imageUrl else item.feed!!.imageUrl,
        item.feed!!.link,
        item.media!!.mimeType,
        item.getPubDate(),
        item.description
    )

    fun getEpisodeIdentifier(): String? {
        return itemIdentifier
    }

    override val websiteLink: String?
        get() = episodeLink ?: feedUrl

    override val identifier: Any
        get() = "$itemIdentifier@$feedUrl"

    override val mediaType: MediaType
        get() = MediaType.fromMimeType(mimeType)

    override val localFileUrl: String?
        get() = null

    override val streamUrl: String?
        get() = downloadUrl

    override fun localFileAvailable(): Boolean {
        return false
    }

    override fun onPlaybackStart() {
        // no-op
    }

    override val playableType: Int
        get() = PLAYABLE_TYPE_REMOTE_MEDIA

    override val imageLocation: String?
        get() = imageUrl

    override fun describeContents(): Int {
        return 0
    }

    override val description: String?
        get() = notes

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(downloadUrl)
        dest.writeString(itemIdentifier)
        dest.writeString(feedUrl)
        dest.writeString(feedTitle)
        dest.writeString(episodeTitle)
        dest.writeString(episodeLink)
        dest.writeString(feedAuthor)
        dest.writeString(imageUrl)
        dest.writeString(feedLink)
        dest.writeString(mimeType)
        dest.writeLong(pubDate?.time ?: 0)
        dest.writeString(notes)
        dest.writeInt(duration)
        dest.writeInt(position)
        dest.writeLong(lastPlayedTimeStatistics)
    }

    override fun equals(other: Any?): Boolean {
        if (other is RemoteMedia) {
            return StringUtils.equals(downloadUrl, other.downloadUrl) &&
                StringUtils.equals(feedUrl, other.feedUrl) &&
                StringUtils.equals(itemIdentifier, other.itemIdentifier)
        }
        if (other is FeedMedia) {
            if (!StringUtils.equals(downloadUrl, other.streamUrl)) {
                return false
            }
            val fi = other.item
            if (fi == null || !StringUtils.equals(itemIdentifier, fi.itemIdentifier)) {
                return false
            }
            val feed = fi.feed
            return feed != null && StringUtils.equals(feedUrl, feed.downloadUrl)
        }
        return false
    }

    override fun hashCode(): Int {
        return HashCodeBuilder()
            .append(downloadUrl)
            .append(feedUrl)
            .append(itemIdentifier)
            .toHashCode()
    }

    companion object {
        const val TAG: String = "RemoteMedia"

        const val PLAYABLE_TYPE_REMOTE_MEDIA: Int = 3

        private const val serialVersionUID = 1L

        @JvmField
        val CREATOR: Parcelable.Creator<RemoteMedia> = object : Parcelable.Creator<RemoteMedia> {
            override fun createFromParcel(parcel: Parcel): RemoteMedia {
                val result = RemoteMedia(
                    parcel.readString(), parcel.readString(), parcel.readString(),
                    parcel.readString(), parcel.readString(), parcel.readString(), parcel.readString(),
                    parcel.readString(), parcel.readString(), parcel.readString(), Date(parcel.readLong()),
                    parcel.readString()
                )
                result.duration = parcel.readInt()
                result.position = parcel.readInt()
                result.lastPlayedTimeStatistics = parcel.readLong()
                return result
            }

            override fun newArray(size: Int): Array<RemoteMedia?> {
                return arrayOfNulls(size)
            }
        }
    }
}
