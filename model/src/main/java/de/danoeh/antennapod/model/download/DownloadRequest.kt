package de.danoeh.antennapod.model.download

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.text.TextUtils

class DownloadRequest(
    val destination: String,
    val source: String,
    val title: String?,
    val feedfileId: Long,
    val feedfileType: Int,
    lastModified: String?,
    var username: String?,
    var password: String?,
    private val mediaEnqueued: Boolean,
    val arguments: Bundle,
    private val initiatedByUser: Boolean
) : Parcelable {

    var lastModified: String? = lastModified
        private set

    var progressPercent: Int = 0
    var soFar: Long = 0
    var size: Long = 0
    private var statusMsg: Int = 0

    constructor(
        destination: String,
        source: String,
        title: String,
        feedfileId: Long,
        feedfileType: Int,
        username: String?,
        password: String?,
        arguments: Bundle,
        initiatedByUser: Boolean
    ) : this(
        destination, source, title, feedfileId, feedfileType, null, username, password, false,
        arguments, initiatedByUser
    )

    private constructor(`in`: Parcel) : this(
        `in`.readString()!!, `in`.readString()!!, `in`.readString(), `in`.readLong(), `in`.readInt(),
        `in`.readString(), nullIfEmpty(`in`.readString()), nullIfEmpty(`in`.readString()), `in`.readByte() > 0,
        `in`.readBundle()!!, `in`.readByte() > 0
    )

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(destination)
        dest.writeString(source)
        dest.writeString(title)
        dest.writeLong(feedfileId)
        dest.writeInt(feedfileType)
        dest.writeString(lastModified)
        // in case of null username/password, still write an empty string
        // (rather than skipping it). Otherwise, unmarshalling  a collection
        // of them from a Parcel (from an Intent extra to submit a request to DownloadService) will fail.
        //
        // see: https://stackoverflow.com/a/22926342
        dest.writeString(nonNullString(username))
        dest.writeString(nonNullString(password))
        dest.writeByte((if (mediaEnqueued) 1 else 0).toByte())
        dest.writeBundle(arguments)
        dest.writeByte((if (initiatedByUser) 1 else 0).toByte())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DownloadRequest) return false

        val that = other

        if (lastModified != that.lastModified) return false
        if (feedfileId != that.feedfileId) return false
        if (feedfileType != that.feedfileType) return false
        if (progressPercent != that.progressPercent) return false
        if (size != that.size) return false
        if (soFar != that.soFar) return false
        if (statusMsg != that.statusMsg) return false
        if (destination != that.destination) return false
        if (password != that.password) return false
        if (source != that.source) return false
        if (title != that.title) return false
        if (username != that.username) return false
        if (mediaEnqueued != that.mediaEnqueued) return false
        if (initiatedByUser != that.initiatedByUser) return false
        return true
    }

    override fun hashCode(): Int {
        var result = destination.hashCode()
        result = 31 * result + source.hashCode()
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + (username?.hashCode() ?: 0)
        result = 31 * result + (password?.hashCode() ?: 0)
        result = 31 * result + (lastModified?.hashCode() ?: 0)
        result = 31 * result + (feedfileId xor (feedfileId ushr 32)).toInt()
        result = 31 * result + feedfileType
        result = 31 * result + arguments.hashCode()
        result = 31 * result + progressPercent
        result = 31 * result + (soFar xor (soFar ushr 32)).toInt()
        result = 31 * result + (size xor (size ushr 32)).toInt()
        result = 31 * result + statusMsg
        result = 31 * result + (if (mediaEnqueued) 1 else 0)
        return result
    }

    fun setStatusMsg(statusMsg: Int) {
        this.statusMsg = statusMsg
    }

    fun setLastModified(lastModified: String?): DownloadRequest {
        this.lastModified = lastModified
        return this
    }

    companion object {
        const val REQUEST_ARG_PAGE_NR: String = "page"

        private fun nonNullString(str: String?): String {
            return str ?: ""
        }

        private fun nullIfEmpty(str: String?): String? {
            return if (TextUtils.isEmpty(str)) null else str
        }

        @JvmField
        val CREATOR: Parcelable.Creator<DownloadRequest> = object : Parcelable.Creator<DownloadRequest> {
            override fun createFromParcel(`in`: Parcel): DownloadRequest {
                return DownloadRequest(`in`)
            }

            override fun newArray(size: Int): Array<DownloadRequest?> {
                return arrayOfNulls(size)
            }
        }
    }
}
