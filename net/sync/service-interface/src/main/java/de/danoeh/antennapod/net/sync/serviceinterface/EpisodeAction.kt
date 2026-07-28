package de.danoeh.antennapod.net.sync.serviceinterface

import android.text.TextUtils
import android.util.Log
import de.danoeh.antennapod.model.feed.FeedItem
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Objects
import java.util.TimeZone
import org.json.JSONException
import org.json.JSONObject

class EpisodeAction private constructor(
    val podcast: String?,
    val episode: String?,
    val guid: String?,
    val action: Action?,
    val timestamp: Date?,
    /**
     * Returns the position (in seconds) at which the client started playback.
     */
    val started: Int,
    /**
     * Returns the position (in seconds) at which the client stopped playback.
     */
    val position: Int,
    /**
     * Returns the total length of the file in seconds.
     */
    val total: Int
) {

    private fun getActionString(): String {
        return action!!.name.lowercase(Locale.US)
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o !is EpisodeAction) {
            return false
        }

        return started == o.started &&
            position == o.position &&
            total == o.total &&
            action != o.action &&
            Objects.equals(podcast, o.podcast) &&
            Objects.equals(episode, o.episode) &&
            Objects.equals(timestamp, o.timestamp) &&
            Objects.equals(guid, o.guid)
    }

    override fun hashCode(): Int {
        var result = podcast?.hashCode() ?: 0
        result = 31 * result + (episode?.hashCode() ?: 0)
        result = 31 * result + (guid?.hashCode() ?: 0)
        result = 31 * result + (action?.hashCode() ?: 0)
        result = 31 * result + (timestamp?.hashCode() ?: 0)
        result = 31 * result + started
        result = 31 * result + position
        result = 31 * result + total
        return result
    }

    /**
     * Returns a JSON object representation of this object.
     *
     * @return JSON object representation, or null if the object is invalid
     */
    fun writeToJsonObject(): JSONObject? {
        val obj = JSONObject()
        try {
            obj.putOpt("podcast", podcast)
            obj.putOpt("episode", episode)
            obj.putOpt("guid", guid)
            obj.put("action", getActionString())
            val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            formatter.timeZone = TimeZone.getTimeZone("UTC")
            obj.put("timestamp", formatter.format(this.timestamp))
            if (this.action == Action.PLAY) {
                obj.put("started", this.started)
                obj.put("position", this.position)
                obj.put("total", this.total)
            }
        } catch (e: JSONException) {
            Log.e(TAG, "writeToJSONObject(): ${e.message}")
            return null
        }
        return obj
    }

    override fun toString(): String {
        return "EpisodeAction{podcast='$podcast', episode='$episode', guid='$guid', action=$action, " +
            "timestamp=$timestamp, started=$started, position=$position, total=$total}"
    }

    enum class Action {
        NEW,
        DOWNLOAD,
        PLAY,
        DELETE
    }

    class Builder {

        // mandatory
        private val podcast: String?
        private val episode: String?
        private val action: Action?

        // optional
        private var timestamp: Date? = null
        private var started = -1
        private var position = -1
        private var total = -1
        private var guid: String? = null

        constructor(item: FeedItem, action: Action?) : this(
            item.feed!!.downloadUrl,
            item.media!!.downloadUrl,
            action
        ) {
            this.guid(item.itemIdentifier)
        }

        constructor(podcast: String?, episode: String?, action: Action?) {
            this.podcast = podcast
            this.episode = episode
            this.action = action
        }

        fun timestamp(timestamp: Date?): Builder {
            this.timestamp = timestamp
            return this
        }

        fun guid(guid: String?): Builder {
            this.guid = guid
            return this
        }

        fun currentTimestamp(): Builder {
            return timestamp(Date())
        }

        fun started(seconds: Int): Builder {
            if (action == Action.PLAY) {
                this.started = seconds
            }
            return this
        }

        fun position(seconds: Int): Builder {
            if (action == Action.PLAY) {
                this.position = seconds
            }
            return this
        }

        fun total(seconds: Int): Builder {
            if (action == Action.PLAY) {
                this.total = seconds
            }
            return this
        }

        fun build(): EpisodeAction {
            return EpisodeAction(podcast, episode, guid, action, timestamp, started, position, total)
        }
    }

    companion object {
        private const val TAG = "EpisodeAction"
        private const val PATTERN_ISO_DATEFORMAT = "yyyy-MM-dd'T'HH:mm:ss"

        @JvmField
        val NEW = Action.NEW

        @JvmField
        val DOWNLOAD = Action.DOWNLOAD

        @JvmField
        val PLAY = Action.PLAY

        @JvmField
        val DELETE = Action.DELETE

        /**
         * Create an episode action object from JSON representation. Mandatory fields are "podcast",
         * "episode" and "action".
         *
         * @param jsonObject JSON representation
         * @return episode action object, or null if mandatory values are missing
         */
        @JvmStatic
        fun readFromJsonObject(jsonObject: JSONObject): EpisodeAction? {
            val podcast = jsonObject.optString("podcast", null)
            val episode = jsonObject.optString("episode", null)
            val actionString = jsonObject.optString("action", null)
            if (TextUtils.isEmpty(podcast) || TextUtils.isEmpty(episode) || TextUtils.isEmpty(actionString)) {
                return null
            }
            val action: Action
            try {
                action = Action.valueOf(actionString.uppercase(Locale.US))
            } catch (e: IllegalArgumentException) {
                return null
            }
            val builder = Builder(podcast, episode, action)
            val utcTimestamp = jsonObject.optString("timestamp", null)
            if (!TextUtils.isEmpty(utcTimestamp)) {
                try {
                    val parser = SimpleDateFormat(PATTERN_ISO_DATEFORMAT, Locale.US)
                    parser.timeZone = TimeZone.getTimeZone("UTC")
                    builder.timestamp(parser.parse(utcTimestamp))
                } catch (e: ParseException) {
                    e.printStackTrace()
                }
            }
            val guid = jsonObject.optString("guid", null)
            if (!TextUtils.isEmpty(guid)) {
                builder.guid(guid)
            }
            if (action == Action.PLAY) {
                val started = jsonObject.optInt("started", -1)
                val position = jsonObject.optInt("position", -1)
                val total = jsonObject.optInt("total", -1)
                if (started >= 0 && position > 0 && total > 0) {
                    builder
                        .started(started)
                        .position(position)
                        .total(total)
                }
            }
            return builder.build()
        }
    }
}
