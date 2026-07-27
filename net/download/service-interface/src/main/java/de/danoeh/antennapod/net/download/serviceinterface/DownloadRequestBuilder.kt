package de.danoeh.antennapod.net.download.serviceinterface

import android.os.Bundle
import de.danoeh.antennapod.model.download.DownloadRequest
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.net.common.UrlChecker

class DownloadRequestBuilder private constructor(
    private val destination: String,
    private var source: String?,
    private val title: String?,
    private val feedfileId: Long,
    private val feedfileType: Int
) {
    private var username: String? = null
    private var password: String? = null
    private var lastModifiedValue: String? = null
    private val arguments = Bundle()
    private var initiatedByUser = true

    constructor(destination: String, media: FeedMedia) : this(
        destination,
        UrlChecker.prepareUrl(media.downloadUrl!!),
        media.humanReadableIdentifier,
        media.id,
        FeedMedia.FEEDFILETYPE_FEEDMEDIA
    )

    constructor(destination: String, feed: Feed) : this(
        destination,
        if (feed.isLocalFeed()) feed.downloadUrl else UrlChecker.prepareUrl(feed.downloadUrl!!),
        feed.getHumanReadableIdentifier(),
        feed.id,
        Feed.FEEDFILETYPE_FEED
    ) {
        arguments.putInt(DownloadRequest.REQUEST_ARG_PAGE_NR, feed.pageNr)
    }

    fun withInitiatedByUser(initiatedByUser: Boolean): DownloadRequestBuilder {
        this.initiatedByUser = initiatedByUser
        return this
    }

    fun setSource(source: String?) {
        this.source = source
    }

    fun setForce(force: Boolean) {
        if (force) {
            lastModifiedValue = null
        }
    }

    fun lastModified(lastModified: String?): DownloadRequestBuilder {
        this.lastModifiedValue = lastModified
        return this
    }

    fun withAuthentication(username: String?, password: String?): DownloadRequestBuilder {
        this.username = username
        this.password = password
        return this
    }

    fun build(): DownloadRequest {
        return DownloadRequest(
            destination, source!!, title, feedfileId, feedfileType,
            lastModifiedValue, username, password, false, arguments, initiatedByUser
        )
    }
}
