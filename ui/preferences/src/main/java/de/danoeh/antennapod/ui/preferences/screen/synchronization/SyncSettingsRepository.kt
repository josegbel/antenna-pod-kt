package de.danoeh.antennapod.ui.preferences.screen.synchronization

import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings
import javax.inject.Inject

interface SyncSettingsRepository {
    fun isProviderConnected(): Boolean
    fun isLastSyncSuccessful(): Boolean
    fun lastSyncAttempt(): Long
    fun syncImmediately()
    fun fullSync()
    fun clearSyncQueue()
}

class DefaultSyncSettingsRepository @Inject constructor() : SyncSettingsRepository {

    private val queue: SynchronizationQueue get() = SynchronizationQueue.instance!!

    override fun isProviderConnected(): Boolean = SynchronizationSettings.isProviderConnected()

    override fun isLastSyncSuccessful(): Boolean = SynchronizationSettings.isLastSyncSuccessful()

    override fun lastSyncAttempt(): Long = SynchronizationSettings.getLastSyncAttempt()

    override fun syncImmediately() = queue.syncImmediately()

    override fun fullSync() = queue.fullSync()

    override fun clearSyncQueue() = queue.clear()
}
