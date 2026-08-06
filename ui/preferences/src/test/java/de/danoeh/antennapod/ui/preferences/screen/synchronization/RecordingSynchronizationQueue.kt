package de.danoeh.antennapod.ui.preferences.screen.synchronization

import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue

/**
 * Test double for [SynchronizationQueue], installed via the mutable
 * [SynchronizationQueue.instance] global (D6) so the slice's calls into it can be observed
 * without any production seam. Records call order across all nine abstract members.
 */
class RecordingSynchronizationQueue : SynchronizationQueue() {
    val calls = mutableListOf<String>()

    /**
     * Invoked with the method name before it is recorded, so a test can snapshot other static
     * state (e.g. SynchronizationSettings/SynchronizationCredentials) at the exact call point to
     * prove ordering -- e.g. gap 7's "provider is still selected at the moment clear() runs".
     */
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
