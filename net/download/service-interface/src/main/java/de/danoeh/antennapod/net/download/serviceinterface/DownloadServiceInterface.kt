package de.danoeh.antennapod.net.download.serviceinterface

import android.content.Context
import de.danoeh.antennapod.model.download.DownloadStatus
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia

abstract class DownloadServiceInterface {
    private var currentDownloads: MutableMap<String?, DownloadStatus> = HashMap()

    fun setCurrentDownloads(currentDownloads: MutableMap<String?, DownloadStatus>) {
        this.currentDownloads = currentDownloads
    }

    /**
     * Download immediately after user action.
     */
    abstract fun downloadNow(context: Context?, item: FeedItem?, ignoreConstraints: Boolean)

    /**
     * Download when device seems fit.
     */
    abstract fun download(context: Context?, item: FeedItem?)

    abstract fun cancel(context: Context?, media: FeedMedia?)

    abstract fun cancelAll(context: Context?)

    fun isDownloadingEpisode(url: String?): Boolean {
        return currentDownloads.containsKey(url) &&
            currentDownloads[url]!!.getState() != DownloadStatus.STATE_COMPLETED
    }

    fun isEpisodeQueued(url: String?): Boolean {
        return currentDownloads.containsKey(url) &&
            currentDownloads[url]!!.getState() == DownloadStatus.STATE_QUEUED
    }

    fun getProgress(url: String?): Int {
        return if (isDownloadingEpisode(url)) currentDownloads[url]!!.getProgress() else -1
    }

    abstract fun getNumberOfActiveDownloads(context: Context?): Int

    companion object {
        const val WORK_TAG = "episodeDownload"
        const val WORK_TAG_EPISODE_URL = "episodeUrl:"
        const val WORK_DATA_PROGRESS = "progress"
        const val WORK_DATA_MEDIA_ID = "media_id"
        const val WORK_DATA_WAS_QUEUED = "was_queued"

        private var impl: DownloadServiceInterface? = null

        @JvmStatic
        fun get(): DownloadServiceInterface? {
            return impl
        }

        @JvmStatic
        fun setImpl(impl: DownloadServiceInterface?) {
            this.impl = impl
        }
    }
}
