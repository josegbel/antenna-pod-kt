package de.danoeh.antennapod.net.download.serviceinterface

import android.util.Log
import android.webkit.URLUtil
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.storage.preferences.UserPreferences
import java.io.File
import org.apache.commons.io.FilenameUtils

/**
 * Creates download requests that can be sent to the DownloadService.
 */
object DownloadRequestCreator {
    private const val TAG = "DownloadRequestCreat"
    private const val FEED_DOWNLOADPATH = "cache/"
    private const val MEDIA_DOWNLOADPATH = "media/"

    @JvmStatic
    fun create(feed: Feed): DownloadRequestBuilder {
        val dest = File(getFeedfilePath(), getFeedfileName(feed))
        if (dest.exists()) {
            val deleted = dest.delete()
            Log.d(TAG, "deleted${dest.path}: $deleted")
        }
        Log.d(TAG, "Requesting download of url ${feed.downloadUrl}")

        val preferences = feed.preferences
        val username = preferences?.getUsername()
        val password = preferences?.getPassword()

        return DownloadRequestBuilder(dest.toString(), feed)
            .withAuthentication(username, password)
            .lastModified(feed.lastModified)
    }

    @JvmStatic
    fun create(media: FeedMedia): DownloadRequestBuilder {
        val localFileUrl = media.localFileUrl
        var dest: File
        val partiallyDownloadedFileExists: Boolean
        if (localFileUrl != null && File(localFileUrl).exists()) {
            dest = File(localFileUrl)
            partiallyDownloadedFileExists = true
        } else {
            dest = File(getMediafilePath(media), getMediafilename(media))
            partiallyDownloadedFileExists = false
        }

        if (dest.exists() && !partiallyDownloadedFileExists) {
            dest = findUnusedFile(dest)
        }
        Log.d(TAG, "Requesting download of url ${media.downloadUrl}")

        val username = media.item!!.feed!!.preferences?.getUsername()
        val password = media.item!!.feed!!.preferences?.getPassword()

        return DownloadRequestBuilder(dest.toString(), media)
            .withAuthentication(username, password)
    }

    private fun findUnusedFile(dest: File): File {
        // find different name
        var newDest: File? = null
        for (i in 1 until Int.MAX_VALUE) {
            val newName = (
                FilenameUtils.getBaseName(dest.name) +
                    "-" +
                    i +
                    FilenameUtils.EXTENSION_SEPARATOR +
                    FilenameUtils.getExtension(dest.name)
                )
            Log.d(TAG, "Testing filename $newName")
            newDest = File(dest.parent, newName)
            if (!newDest.exists()) {
                Log.d(TAG, "File doesn't exist yet. Using $newName")
                break
            }
        }
        return newDest!!
    }

    private fun getFeedfilePath(): String {
        return UserPreferences.getDataFolder(FEED_DOWNLOADPATH).toString() + "/"
    }

    private fun getFeedfileName(feed: Feed): String {
        var filename = feed.downloadUrl
        val title = feed.title
        if (!title.isNullOrEmpty()) {
            filename = title
        }
        return "feed-" + FileNameGenerator.generateFileName(filename) + feed.id
    }

    private fun getMediafilePath(media: FeedMedia): String {
        val mediaPath = MEDIA_DOWNLOADPATH +
            FileNameGenerator.generateFileName(media.item!!.feed!!.title)
        return UserPreferences.getDataFolder(mediaPath).toString() + "/"
    }

    private fun getMediafilename(media: FeedMedia): String {
        var titleBaseFilename = ""

        // Try to generate the filename by the item title
        val item = media.item
        if (item != null && item.title != null) {
            val title = item.title
            titleBaseFilename = FileNameGenerator.generateFileName(title)
        }

        val urlBaseFilename = URLUtil.guessFileName(media.downloadUrl, null, media.mimeType)

        val baseFilename = if (titleBaseFilename != "") {
            titleBaseFilename
        } else {
            urlBaseFilename
        }
        val filenameMaxLength = 220
        val truncatedBaseFilename = if (baseFilename.length > filenameMaxLength) {
            baseFilename.substring(0, filenameMaxLength)
        } else {
            baseFilename
        }
        return truncatedBaseFilename + FilenameUtils.EXTENSION_SEPARATOR + media.id +
            FilenameUtils.EXTENSION_SEPARATOR + FilenameUtils.getExtension(urlBaseFilename)
    }
}
