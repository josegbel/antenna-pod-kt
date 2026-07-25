package de.danoeh.antennapod.model.playback

import android.os.Parcelable
import de.danoeh.antennapod.model.feed.Chapter
import java.io.Serializable
import java.util.Date

/**
 * Interface for objects that can be played by the PlaybackService.
 */
interface Playable : Parcelable, Serializable {

    /**
     * Returns the title of the episode that this playable represents
     */
    val episodeTitle: String?

    /**
     * List of chapter marks or null if this Playable has no chapters.
     */
    var chapters: List<Chapter>?

    /**
     * A link to a website that is meant to be shown in a browser
     */
    val websiteLink: String?

    /**
     * The title of the feed this Playable belongs to.
     */
    val feedTitle: String?

    /**
     * The published date
     */
    val pubDate: Date?

    /**
     * A unique identifier, for example a file url or an ID from a
     * database.
     */
    val identifier: Any

    /**
     * Duration of object or 0 if duration is unknown.
     */
    var duration: Int

    /**
     * Position of object or 0 if position is unknown.
     */
    var position: Int

    /**
     * Last time (in ms) when this playable was played or 0
     * if last played time is unknown.
     */
    var lastPlayedTimeStatistics: Long

    /**
     * The description of the item, if available.
     * For FeedItems, the description needs to be loaded from the database first.
     */
    val description: String?

    /**
     * The type of media.
     */
    val mediaType: MediaType

    /**
     * A url to a local file that can be played or null if this file
     * does not exist.
     */
    val localFileUrl: String?

    /**
     * A url to a file that can be streamed by the player or null if
     * this url is not known.
     */
    val streamUrl: String?

    /**
     * Returns true if a local file that can be played is available. getFileUrl
     * MUST return a non-null string if this method returns true.
     */
    fun localFileAvailable(): Boolean

    /**
     * This method should be called every time playback starts on this object.
     *
     * Position held by this Playable should be set accurately before a call to this method is made.
     */
    fun onPlaybackStart()

    /**
     * An integer that must be unique among all Playable classes. The
     * value is later used by PlaybackPreferences to determine the type of the
     * Playable object that is restored.
     */
    val playableType: Int

    /**
     * The location of the image or null if no image is available.
     * This can be the feed item image URL, the local embedded media image path, the feed image URL,
     * or the remote media image URL, depending on what's available.
     */
    val imageLocation: String?

    companion object {
        const val INVALID_TIME: Int = -1
    }
}
