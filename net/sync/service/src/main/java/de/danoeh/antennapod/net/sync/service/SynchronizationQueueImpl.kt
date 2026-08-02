package de.danoeh.antennapod.net.sync.service

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import de.danoeh.antennapod.event.SyncServiceEvent
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings
import de.danoeh.antennapod.storage.preferences.UserPreferences
import java.util.concurrent.TimeUnit
import org.greenrobot.eventbus.EventBus

class SynchronizationQueueImpl(private val context: Context) : SynchronizationQueue() {

    override fun sync() {
        val workRequest = getWorkRequest().build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_ID_SYNC, ExistingWorkPolicy.REPLACE, workRequest)
    }

    override fun syncIfNotSyncedRecently() {
        if (System.currentTimeMillis() - SynchronizationSettings.getLastSyncAttempt() > 1000 * 60 * 10) {
            sync()
        }
    }

    override fun syncImmediately() {
        val workRequest = getWorkRequest()
            .setInitialDelay(0L, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_ID_SYNC, ExistingWorkPolicy.REPLACE, workRequest)
    }

    override fun fullSync() {
        LockingAsyncExecutor.executeLockedAsync(
            Runnable {
                SynchronizationSettings.resetTimestamps()
                syncImmediately()
            }
        )
    }

    override fun clear() {
        LockingAsyncExecutor.executeLockedAsync(Runnable { SynchronizationQueueStorage(context).clearQueue() })
    }

    override fun enqueueFeedAdded(downloadUrl: String?) {
        if (!SynchronizationSettings.isProviderConnected()) {
            return
        }
        LockingAsyncExecutor.executeLockedAsync(
            Runnable {
                SynchronizationQueueStorage(context).enqueueFeedAdded(downloadUrl)
                sync()
            }
        )
    }

    override fun enqueueFeedRemoved(downloadUrl: String?) {
        if (!SynchronizationSettings.isProviderConnected()) {
            return
        }
        LockingAsyncExecutor.executeLockedAsync(
            Runnable {
                SynchronizationQueueStorage(context).enqueueFeedRemoved(downloadUrl)
                sync()
            }
        )
    }

    override fun enqueueEpisodeAction(action: EpisodeAction?) {
        if (!SynchronizationSettings.isProviderConnected()) {
            return
        }
        LockingAsyncExecutor.executeLockedAsync(
            Runnable {
                SynchronizationQueueStorage(context).enqueueEpisodeAction(action)
                sync()
            }
        )
    }

    override fun enqueueEpisodePlayed(media: FeedMedia?, completed: Boolean) {
        if (!SynchronizationSettings.isProviderConnected()) {
            return
        }
        // media is forced nullable by SynchronizationQueue's abstract method (Rule M11); the
        // connectivity guard above runs first, so a null media is a benign no-op for any user
        // with sync unconfigured, and an NPE otherwise -- matching Java's unguarded dereference (D7).
        if (media!!.item == null || media.item!!.feed!!.isLocalFeed() ||
            media.item!!.feed!!.getState() == Feed.STATE_NOT_SUBSCRIBED
        ) {
            return
        }
        if (media.startPosition < 0 || (!completed && media.startPosition >= media.position)) {
            return
        }
        // item was already asserted non-null by the guard above; Builder's `item` parameter is
        // itself declared non-null (EpisodeAction.kt), so this is also required to compile.
        val action = EpisodeAction.Builder(media.item!!, EpisodeAction.PLAY)
            .currentTimestamp()
            .started(media.startPosition / 1000)
            .position((if (completed) media.duration else media.position) / 1000)
            .total(media.duration / 1000)
            .build()
        enqueueEpisodeAction(action)
    }

    companion object {
        private const val WORK_ID_SYNC = "SyncServiceWorkId"

        private fun getWorkRequest(): OneTimeWorkRequest.Builder {
            val constraints = Constraints.Builder()
            if (UserPreferences.isAllowMobileSync()) {
                constraints.setRequiredNetworkType(NetworkType.CONNECTED)
            } else {
                constraints.setRequiredNetworkType(NetworkType.UNMETERED)
            }

            val builder = OneTimeWorkRequest.Builder(SyncService::class.java)
                .setConstraints(constraints.build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)

            if (SyncService.isCurrentlyActive()) {
                // Debounce: don't start sync again immediately after it was finished.
                builder.setInitialDelay(2L, TimeUnit.MINUTES)
            } else {
                // Give it some time, so other possible actions can be queued.
                builder.setInitialDelay(20L, TimeUnit.SECONDS)
                EventBus.getDefault().postSticky(SyncServiceEvent(R.string.sync_status_started))
            }
            return builder
        }
    }
}
