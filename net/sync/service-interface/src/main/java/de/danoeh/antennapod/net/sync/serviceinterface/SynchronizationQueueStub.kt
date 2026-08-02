package de.danoeh.antennapod.net.sync.serviceinterface

import de.danoeh.antennapod.model.feed.FeedMedia

class SynchronizationQueueStub : SynchronizationQueue() {
    override fun sync() {
    }

    override fun syncImmediately() {
    }

    override fun fullSync() {
    }

    override fun syncIfNotSyncedRecently() {
    }

    override fun clear() {
    }

    override fun enqueueFeedAdded(downloadUrl: String?) {
    }

    override fun enqueueFeedRemoved(downloadUrl: String?) {
    }

    override fun enqueueEpisodeAction(action: EpisodeAction?) {
    }

    override fun enqueueEpisodePlayed(media: FeedMedia?, completed: Boolean) {
    }
}
