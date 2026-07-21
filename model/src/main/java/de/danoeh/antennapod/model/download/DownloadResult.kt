package de.danoeh.antennapod.model.download

import java.io.Serializable
import java.util.Date

/**
 * Contains status attributes for one download
 */
class DownloadResult(
    id: Long,
    title: String?,
    feedfileId: Long,
    feedfileType: Int,
    successful: Boolean,
    reason: DownloadError?,
    completionDate: Date,
    reasonDetailed: String?
) : Serializable {

    /**
     * A human-readable string which is shown to the user so that he can
     * identify the download. Should be the title of the item/feed/media or the
     * URL if the download has no other title.
     */
    private val title: String? = title
    private val feedfileId: Long = feedfileId

    /**
     * Is used to determine the type of the feedfile even if the feedfile does
     * not exist anymore. The value should be FEEDFILETYPE_FEED,
     * FEEDFILETYPE_FEEDIMAGE or FEEDFILETYPE_FEEDMEDIA
     */
    private val feedfileType: Int = feedfileType

    /**
     * Unique id for storing the object in database.
     */
    private var id: Long = id
    private var reason: DownloadError? = reason

    /**
     * A message which can be presented to the user to give more information.
     * Should be null if Download was successful.
     */
    private var reasonDetailed: String? = reasonDetailed
    private var successful: Boolean = successful
    private val completionDate: Date = completionDate.clone() as Date

    constructor(
        title: String?,
        feedfileId: Long,
        feedfileType: Int,
        successful: Boolean,
        reason: DownloadError?,
        reasonDetailed: String?
    ) : this(0, title, feedfileId, feedfileType, successful, reason, Date(), reasonDetailed)

    override fun toString(): String {
        return "DownloadStatus [id=$id, title=$title, reason=$reason, reasonDetailed=$reasonDetailed" +
            ", successful=$successful, completionDate=$completionDate, feedfileId=$feedfileId" +
            ", feedfileType=$feedfileType]"
    }

    fun getId(): Long {
        return id
    }

    fun setId(id: Long) {
        this.id = id
    }

    fun getTitle(): String? {
        return title
    }

    fun getReason(): DownloadError? {
        return reason
    }

    fun getReasonDetailed(): String? {
        return reasonDetailed
    }

    fun isSuccessful(): Boolean {
        return successful
    }

    fun getCompletionDate(): Date {
        return completionDate.clone() as Date
    }

    fun getFeedfileId(): Long {
        return feedfileId
    }

    fun getFeedfileType(): Int {
        return feedfileType
    }

    fun setSuccessful() {
        this.successful = true
        this.reason = DownloadError.SUCCESS
    }

    fun setFailed(reason: DownloadError?, reasonDetailed: String?) {
        this.successful = false
        this.reason = reason
        this.reasonDetailed = reasonDetailed
    }

    fun setCancelled() {
        this.successful = false
        this.reason = DownloadError.ERROR_DOWNLOAD_CANCELLED
    }

    companion object {
        private const val serialVersionUID = 1L

        /**
         * Downloaders should use this constant for the size attribute if necessary
         * so that the listadapters etc. can react properly.
         */
        const val SIZE_UNKNOWN = -1
    }
}
