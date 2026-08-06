package de.danoeh.antennapod.ui.preferences.screen.synchronization

import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue

class RecordingSynchronizationQueue : SynchronizationQueue() {
    val calls = mutableListOf<String>()

    var onCall: ((String) -> Unit)? = null

    private fun record(name: String) {
        onCall?.invoke(name)
        calls.add(name)
    }

    override fun sync() {
        record("sync")
    }

    override fun syncImmediately() {
        record("syncImmediately")
    }

    override fun fullSync() {
        record("fullSync")
    }

    override fun syncIfNotSyncedRecently() {
        record("syncIfNotSyncedRecently")
    }

    override fun clear() {
        record("clear")
    }

    override fun enqueueFeedAdded(downloadUrl: String?) {
        record("enqueueFeedAdded")
    }

    override fun enqueueFeedRemoved(downloadUrl: String?) {
        record("enqueueFeedRemoved")
    }

    override fun enqueueEpisodeAction(action: EpisodeAction?) {
        record("enqueueEpisodeAction")
    }

    override fun enqueueEpisodePlayed(media: FeedMedia?, completed: Boolean) {
        record("enqueueEpisodePlayed")
    }
}
