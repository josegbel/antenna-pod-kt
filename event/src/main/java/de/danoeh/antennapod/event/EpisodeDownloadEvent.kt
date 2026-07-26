package de.danoeh.antennapod.event

import de.danoeh.antennapod.model.download.DownloadStatus
import de.danoeh.antennapod.model.feed.FeedItem

class EpisodeDownloadEvent(private val map: Map<String, DownloadStatus>) {

    fun getUrls(): Set<String> {
        return map.keys
    }

    companion object {
        @JvmStatic
        fun indexOfItemWithDownloadUrl(items: List<FeedItem?>, downloadUrl: String?): Int {
            return items.indexOfFirst { item ->
                val media = item?.media
                media != null && media.downloadUrl!! == downloadUrl
            }
        }
    }
}
