package de.danoeh.antennapod.net.sync.service

import android.content.Context
import android.content.SharedPreferences
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings
import org.json.JSONArray
import org.json.JSONException

class SynchronizationQueueStorage(context: Context) {

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun getQueuedEpisodeActions(): ArrayList<EpisodeAction?> {
        val actions = ArrayList<EpisodeAction?>()
        try {
            val json = getSharedPreferences()
                .getString(QUEUED_EPISODE_ACTIONS, "[]")
            val queue = JSONArray(json)
            for (i in 0 until queue.length()) {
                actions.add(EpisodeAction.readFromJsonObject(queue.getJSONObject(i)))
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return actions
    }

    fun getQueuedRemovedFeeds(): ArrayList<String> {
        val removedFeedUrls = ArrayList<String>()
        try {
            val json = getSharedPreferences()
                .getString(QUEUED_FEEDS_REMOVED, "[]")
            val queue = JSONArray(json)
            for (i in 0 until queue.length()) {
                removedFeedUrls.add(queue.getString(i))
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return removedFeedUrls
    }

    fun getQueuedAddedFeeds(): ArrayList<String> {
        val addedFeedUrls = ArrayList<String>()
        try {
            val json = getSharedPreferences()
                .getString(QUEUED_FEEDS_ADDED, "[]")
            val queue = JSONArray(json)
            for (i in 0 until queue.length()) {
                addedFeedUrls.add(queue.getString(i))
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return addedFeedUrls
    }

    fun clearEpisodeActionQueue() {
        getSharedPreferences().edit()
            .putString(QUEUED_EPISODE_ACTIONS, "[]").apply()
    }

    fun clearFeedQueues() {
        getSharedPreferences().edit()
            .putString(QUEUED_FEEDS_ADDED, "[]")
            .putString(QUEUED_FEEDS_REMOVED, "[]")
            .apply()
    }

    @JvmName("clearQueue")
    internal fun clearQueue() {
        SynchronizationSettings.resetTimestamps()
        getSharedPreferences().edit()
            .putString(QUEUED_EPISODE_ACTIONS, "[]")
            .putString(QUEUED_FEEDS_ADDED, "[]")
            .putString(QUEUED_FEEDS_REMOVED, "[]")
            .apply()
    }

    @JvmName("enqueueFeedAdded")
    internal fun enqueueFeedAdded(downloadUrl: String?) {
        val sharedPreferences = getSharedPreferences()
        try {
            val addedQueue = JSONArray(sharedPreferences.getString(QUEUED_FEEDS_ADDED, "[]"))
            addedQueue.put(downloadUrl)
            val removedQueue = JSONArray(sharedPreferences.getString(QUEUED_FEEDS_REMOVED, "[]"))
            removedQueue.remove(indexOf(downloadUrl, removedQueue))
            sharedPreferences.edit()
                .putString(QUEUED_FEEDS_ADDED, addedQueue.toString())
                .putString(QUEUED_FEEDS_REMOVED, removedQueue.toString())
                .apply()
        } catch (jsonException: JSONException) {
            jsonException.printStackTrace()
        }
    }

    /** Remove feed entries that conflict with the given list of current local subscriptions.
     * <>
     * This is only relevant for old clients that have legacy data. In newer versions, `enqueueFeedAdded`
     * and `enqueueFeedAdded` already take care of removing conflicting entries.
     * */
    @JvmName("removeLegacyConflictingFeedEntries")
    internal fun removeLegacyConflictingFeedEntries(currentLocalSubscriptions: Collection<String>) {
        val removedQueue = this.getQueuedRemovedFeeds()
        val addedQueue = this.getQueuedAddedFeeds()
        removedQueue.removeAll(currentLocalSubscriptions)
        addedQueue.removeAll(removedQueue)
        sharedPreferences.edit()
            .putString(QUEUED_FEEDS_ADDED, addedQueue.toString())
            .putString(QUEUED_FEEDS_REMOVED, removedQueue.toString())
            .apply()
    }

    @JvmName("enqueueFeedRemoved")
    internal fun enqueueFeedRemoved(downloadUrl: String?) {
        val sharedPreferences = getSharedPreferences()
        try {
            val removedQueue = JSONArray(sharedPreferences.getString(QUEUED_FEEDS_REMOVED, "[]"))
            removedQueue.put(downloadUrl)
            val addedQueue = JSONArray(sharedPreferences.getString(QUEUED_FEEDS_ADDED, "[]"))
            addedQueue.remove(indexOf(downloadUrl, addedQueue))
            sharedPreferences.edit()
                .putString(QUEUED_FEEDS_ADDED, addedQueue.toString())
                .putString(QUEUED_FEEDS_REMOVED, removedQueue.toString())
                .apply()
        } catch (jsonException: JSONException) {
            jsonException.printStackTrace()
        }
    }

    private fun indexOf(string: String?, array: JSONArray): Int {
        try {
            for (i in 0 until array.length()) {
                if (array.getString(i) == string) {
                    return i
                }
            }
        } catch (jsonException: JSONException) {
            jsonException.printStackTrace()
        }
        return -1
    }

    @JvmName("enqueueEpisodeAction")
    internal fun enqueueEpisodeAction(action: EpisodeAction?) {
        val sharedPreferences = getSharedPreferences()
        val json = sharedPreferences.getString(QUEUED_EPISODE_ACTIONS, "[]")
        try {
            val queue = JSONArray(json)
            // action is nullable end-to-end from SynchronizationQueue's abstract method (Rule M11);
            // Java's action.writeToJsonObject() NPEs on a null action today (D7), so this must too.
            queue.put(action!!.writeToJsonObject())
            sharedPreferences.edit().putString(
                QUEUED_EPISODE_ACTIONS,
                queue.toString()
            ).apply()
        } catch (jsonException: JSONException) {
            jsonException.printStackTrace()
        }
    }

    private fun getSharedPreferences(): SharedPreferences {
        return sharedPreferences
    }

    companion object {
        private const val NAME = "synchronization"
        private const val QUEUED_EPISODE_ACTIONS = "sync_queued_episode_actions"
        private const val QUEUED_FEEDS_REMOVED = "sync_removed"
        private const val QUEUED_FEEDS_ADDED = "sync_added"
    }
}
