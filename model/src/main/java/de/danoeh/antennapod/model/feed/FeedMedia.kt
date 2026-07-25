package de.danoeh.antennapod.model.feed

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import de.danoeh.antennapod.model.MediaMetadataRetrieverCompat
import de.danoeh.antennapod.model.playback.MediaType
import de.danoeh.antennapod.model.playback.Playable
import de.danoeh.antennapod.model.playback.RemoteMedia
import java.io.File
import java.util.Date
import org.apache.commons.lang3.StringUtils

class FeedMedia(
    id: Long,
    item: FeedItem?,
    duration: Int,
    position: Int,
    size: Long,
    mimeType: String?,
    localFileUrl: String?,
    downloadUrl: String?,
    downloadDate: Long,
    lastPlayedTimeHistory: Date?,
    playedDuration: Int,
    lastPlayedTimeStatistics: Long
) : Playable {

    var id: Long = id

    @Volatile
    var item: FeedItem? = item
        set(value) {
            field = value
            itemId = value?.id ?: 0
            if (value != null && value.media !== this) {
                value.media = this
            }
        }

    override var duration: Int = duration

    override var position: Int = position
        set(value) {
            field = value
            val currentItem = item
            if (value > 0 && currentItem != null && currentItem.isNew) {
                currentItem.isPlayed = false
            }
        }

    var size: Long = size

    var mimeType: String? = mimeType
        private set

    override var localFileUrl: String? = localFileUrl
        private set

    var downloadUrl: String? = downloadUrl
        private set

    var downloadDate: Long = downloadDate
        private set

    private var lastPlayedTimeHistoryField: Date? = lastPlayedTimeHistory?.clone() as Date?

    var playedDuration: Int = playedDuration

    override var lastPlayedTimeStatistics: Long = lastPlayedTimeStatistics

    var itemId: Long = item?.id ?: 0

    var startPosition: Int = -1
        private set

    var playedDurationWhenStarted: Int = playedDuration
        private set

    // if null: unknown, will be checked
    private var hasEmbeddedPictureField: Boolean? = null

    constructor(item: FeedItem?, downloadUrl: String?, size: Long, mimeType: String?) : this(
        0L, item, 0, 0, size, mimeType, null, downloadUrl, 0L, null, 0, 0L
    )

    constructor(
        id: Long,
        item: FeedItem?,
        duration: Int,
        position: Int,
        size: Long,
        mimeType: String?,
        localFileUrl: String?,
        downloadUrl: String?,
        downloadDate: Long,
        lastPlayedTimeHistory: Date?,
        playedDuration: Int,
        hasEmbeddedPicture: Boolean?,
        lastPlayedTimeStatistics: Long
    ) : this(
        id, item, duration, position, size, mimeType, localFileUrl, downloadUrl, downloadDate,
        lastPlayedTimeHistory, playedDuration, lastPlayedTimeStatistics
    ) {
        hasEmbeddedPictureField = hasEmbeddedPicture
    }

    val humanReadableIdentifier: String?
        get() {
            val currentItem = item
            return if (currentItem != null && currentItem.title != null) currentItem.title else downloadUrl
        }

    /**
     * Returns a MediaItem representing the FeedMedia object.
     * This is used by the MediaBrowserService
     */
    fun getMediaItem(): MediaBrowserCompat.MediaItem {
        val builder = MediaDescriptionCompat.Builder()
            .setMediaId(id.toString())
            .setTitle(episodeTitle)
            .setDescription(feedTitle)
            .setSubtitle(feedTitle)
        val currentItem = item
        if (currentItem != null) {
            // getImageLocation() also loads embedded images, which we can not send to external devices
            val itemImageUrl = currentItem.imageUrl
            if (itemImageUrl != null) {
                builder.setIconUri(Uri.parse(itemImageUrl))
            } else {
                val feedImageUrl = currentItem.feed?.imageUrl
                if (feedImageUrl != null) {
                    builder.setIconUri(Uri.parse(feedImageUrl))
                }
            }
        }
        return MediaBrowserCompat.MediaItem(builder.build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
    }

    override val mediaType: MediaType
        get() = MediaType.fromMimeType(mimeType)

    fun updateFromOther(other: FeedMedia) {
        downloadUrl = other.downloadUrl
        if (other.size > 0) {
            size = other.size
        }
        if (other.duration > 0 && duration <= 0) { // Do not overwrite duration that we measured after downloading
            duration = other.duration
        }
        if (other.mimeType != null) {
            mimeType = other.mimeType
        }
    }

    /**
     * Compare's this FeedFile's attribute values with another FeedFile's
     * attribute values. This method will only compare attributes which were
     * read from the feed.
     *
     * @return true if attribute values are different, false otherwise
     */
    fun compareWithOther(other: FeedMedia): Boolean {
        if (!StringUtils.equals(downloadUrl, other.downloadUrl)) {
            return true
        }
        if (other.mimeType != null) {
            if (mimeType == null || mimeType != other.mimeType) {
                return true
            }
        }
        if (other.size > 0 && other.size != size) {
            return true
        }
        if (other.duration > 0 && duration <= 0) {
            return true
        }
        return false
    }

    override val description: String?
        get() = item?.description

    /**
     * Indicates we asked the service what the size was, but didn't
     * get a valid answer and we shoudln't check using the network again.
     */
    fun setCheckedOnSizeButUnknown() {
        size = CHECKED_ON_SIZE_BUT_UNKNOWN.toLong()
    }

    fun checkedOnSizeButUnknown(): Boolean {
        return CHECKED_ON_SIZE_BUT_UNKNOWN.toLong() == size
    }

    var lastPlayedTimeHistory: Date?
        get() = lastPlayedTimeHistoryField?.clone() as Date?
        set(value) {
            lastPlayedTimeHistoryField = value?.clone() as Date?
        }

    val isInProgress: Boolean
        get() = position > 0

    override fun describeContents(): Int {
        return 0
    }

    fun hasEmbeddedPicture(): Boolean {
        if (hasEmbeddedPictureField == null) {
            checkEmbeddedPicture()
        }
        return hasEmbeddedPictureField!!
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeLong(id)
        dest.writeLong(item?.id ?: 0L)

        dest.writeInt(duration)
        dest.writeInt(position)
        dest.writeLong(size)
        dest.writeString(mimeType)
        dest.writeString(localFileUrl)
        dest.writeString(downloadUrl)
        dest.writeLong(downloadDate)
        dest.writeLong(lastPlayedTimeHistoryField?.time ?: 0)
        dest.writeInt(playedDuration)
        dest.writeLong(lastPlayedTimeStatistics)
    }

    override val episodeTitle: String?
        get() {
            val currentItem = item ?: return null
            return currentItem.title ?: currentItem.getIdentifyingValue()
        }

    override var chapters: List<Chapter>?
        get() = item?.chapters
        set(value) {
            // FeedItem.chapters is MutableList<Chapter>? (Milestone 4's own conversion choice),
            // while Playable.chapters is List<Chapter>? (this milestone's resolved decision) -
            // this cast bridges Kotlin's invariant generics, mirroring the Java original's raw
            // reference assignment (setChapters(List<Chapter>) into a List-typed field with no
            // runtime check either).
            @Suppress("UNCHECKED_CAST")
            item?.chapters = value as MutableList<Chapter>?
        }

    override val websiteLink: String?
        get() = item?.link

    override val feedTitle: String?
        get() = item?.feed?.title

    override val identifier: Any
        get() = id

    override val streamUrl: String?
        get() = downloadUrl

    override val pubDate: Date?
        get() = item?.getPubDate()

    override fun localFileAvailable(): Boolean {
        return isDownloaded && localFileUrl != null
    }

    fun fileExists(): Boolean {
        val fileUrl = localFileUrl ?: return false
        return File(fileUrl).exists()
    }

    val isDownloaded: Boolean
        get() {
            val currentItem = item
            val currentFeed = currentItem?.feed
            return downloadDate > 0 || (currentFeed != null && currentFeed.isLocalFeed())
        }

    override fun onPlaybackStart() {
        startPosition = maxOf(position, 0)
        playedDurationWhenStarted = playedDuration
    }

    override val playableType: Int
        get() = PLAYABLE_TYPE_FEEDMEDIA

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null) {
            return false
        }
        if (other is RemoteMedia) {
            return other.equals(this)
        }
        if (javaClass != other.javaClass) {
            return false
        }
        val feedMedia = other as FeedMedia
        return id == feedMedia.id
    }

    override val imageLocation: String?
        get() {
            val currentItem = item
            return when {
                currentItem != null -> currentItem.getImageLocation()
                hasEmbeddedPicture() -> FILENAME_PREFIX_EMBEDDED_COVER + localFileUrl
                else -> null
            }
        }

    fun setHasEmbeddedPicture(hasEmbeddedPicture: Boolean?) {
        hasEmbeddedPictureField = hasEmbeddedPicture
    }

    fun setDownloaded(downloaded: Boolean, whenDownloaded: Long) {
        downloadDate = if (downloaded) whenDownloaded else 0
        val currentItem = item
        if (currentItem != null && downloaded && currentItem.isNew) {
            currentItem.isPlayed = false
        }
    }

    fun setLocalFileUrl(fileUrl: String?) {
        localFileUrl = fileUrl
        if (fileUrl == null) {
            downloadDate = 0
        }
    }

    fun checkEmbeddedPicture() {
        if (!localFileAvailable()) {
            hasEmbeddedPictureField = false
            return
        }
        val mmr = MediaMetadataRetrieverCompat()
        try {
            mmr.setDataSource(localFileUrl)
            val image = mmr.getEmbeddedPicture()
            hasEmbeddedPictureField = image != null
        } catch (e: Exception) {
            e.printStackTrace()
            hasEmbeddedPictureField = false
        } finally {
            mmr.close()
        }
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    val transcriptFileUrl: String?
        get() {
            val fileUrl = localFileUrl ?: return null
            return "$fileUrl.transcript"
        }

    var transcript: Transcript?
        get() = item?.transcript
        set(value) {
            item?.transcript = value
        }

    fun hasTranscript(): Boolean {
        val currentItem = item ?: return false
        return currentItem.hasTranscript()
    }

    companion object {
        const val FEEDFILETYPE_FEEDMEDIA: Int = 2
        const val PLAYABLE_TYPE_FEEDMEDIA: Int = 1
        const val FILENAME_PREFIX_EMBEDDED_COVER: String = "metadata-retriever:"

        /**
         * Indicates we've checked on the size of the item via the network
         * and got an invalid response. Using Integer.MIN_VALUE because
         * 1) we'll still check on it in case it gets downloaded (it's <= 0)
         * 2) By default all FeedMedia have a size of 0 if we don't know it,
         *    so this won't conflict with existing practice.
         */
        private const val CHECKED_ON_SIZE_BUT_UNKNOWN: Int = Int.MIN_VALUE

        private const val serialVersionUID = 1L

        @JvmField
        val CREATOR: Parcelable.Creator<FeedMedia> = object : Parcelable.Creator<FeedMedia> {
            override fun createFromParcel(parcel: Parcel): FeedMedia {
                val id = parcel.readLong()
                val itemID = parcel.readLong()
                val result = FeedMedia(
                    id, null, parcel.readInt(), parcel.readInt(), parcel.readLong(), parcel.readString(),
                    parcel.readString(), parcel.readString(), parcel.readLong(), Date(parcel.readLong()),
                    parcel.readInt(), parcel.readLong()
                )
                result.itemId = itemID
                return result
            }

            override fun newArray(size: Int): Array<FeedMedia?> {
                return arrayOfNulls(size)
            }
        }
    }
}
