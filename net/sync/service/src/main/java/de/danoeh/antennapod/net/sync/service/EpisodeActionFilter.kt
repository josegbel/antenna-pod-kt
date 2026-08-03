package de.danoeh.antennapod.net.sync.service

import android.util.Log
import androidx.collection.ArrayMap
import androidx.core.util.Pair
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction

object EpisodeActionFilter {

    const val TAG = "EpisodeActionFilter"

    @JvmStatic
    fun getRemoteActionsOverridingLocalActions(
        remoteActions: List<EpisodeAction>,
        queuedEpisodeActions: List<EpisodeAction?>
    ): Map<Pair<String?, String?>, EpisodeAction> {
        // make sure more recent local actions are not overwritten by older remote actions
        val remoteActionsThatOverrideLocalActions: MutableMap<Pair<String?, String?>, EpisodeAction> = ArrayMap()
        val localMostRecentPlayActions = createUniqueLocalMostRecentPlayActions(queuedEpisodeActions)
        for (remoteAction in remoteActions) {
            val key = Pair(remoteAction.podcast, remoteAction.episode)
            // The dereference below reproduces Java's switch, which NPEs on a null enum (D6); a
            // `when` on the nullable `action` would route null to `else` and log instead of throw.
            when (remoteAction.action!!) {
                EpisodeAction.Action.NEW, EpisodeAction.Action.DOWNLOAD -> {
                }
                EpisodeAction.Action.PLAY -> {
                    val localMostRecent = localMostRecentPlayActions[key]
                    if (secondActionOverridesFirstAction(remoteAction, localMostRecent)) {
                        continue
                    }
                    val remoteMostRecentAction = remoteActionsThatOverrideLocalActions[key]
                    if (secondActionOverridesFirstAction(remoteAction, remoteMostRecentAction)) {
                        continue
                    }
                    remoteActionsThatOverrideLocalActions[key] = remoteAction
                }
                EpisodeAction.Action.DELETE -> {
                    // NEVER EVER call DBWriter.deleteFeedMediaOfItem() here, leads to an infinite loop
                }
                else -> {
                    Log.e(TAG, "Unknown remoteAction: $remoteAction")
                }
            }
        }

        return remoteActionsThatOverrideLocalActions
    }

    private fun createUniqueLocalMostRecentPlayActions(
        queuedEpisodeActions: List<EpisodeAction?>
    ): Map<Pair<String?, String?>, EpisodeAction> {
        val localMostRecentPlayAction: MutableMap<Pair<String?, String?>, EpisodeAction> = ArrayMap()
        for (action in queuedEpisodeActions) {
            // The list may contain nulls (D3); Java's action.getPodcast() NPEs here today too.
            val key = Pair(action!!.podcast, action.episode)
            val mostRecent = localMostRecentPlayAction[key]
            if (mostRecent == null || mostRecent.timestamp == null) {
                localMostRecentPlayAction[key] = action
            } else if (mostRecent.timestamp!!.before(action.timestamp)) {
                // `timestamp` is a cross-module val, so the preceding null check grants no smart cast.
                localMostRecentPlayAction[key] = action
            }
        }
        return localMostRecentPlayAction
    }

    private fun secondActionOverridesFirstAction(firstAction: EpisodeAction, secondAction: EpisodeAction?): Boolean {
        return secondAction != null &&
            secondAction.timestamp != null &&
            // Guarded by the `secondAction.timestamp != null` clause just above; still needs `!!`
            // because `timestamp` is a cross-module val and grants no smart cast here.
            (firstAction.timestamp == null || secondAction.timestamp!!.after(firstAction.timestamp))
    }
}
