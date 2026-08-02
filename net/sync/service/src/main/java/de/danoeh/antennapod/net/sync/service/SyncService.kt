package de.danoeh.antennapod.net.sync.service

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import de.danoeh.antennapod.event.FeedUpdateRunningEvent
import de.danoeh.antennapod.event.MessageEvent
import de.danoeh.antennapod.event.SyncServiceEvent
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter
import de.danoeh.antennapod.model.feed.SortOrder
import de.danoeh.antennapod.net.common.AntennapodHttpClient
import de.danoeh.antennapod.net.common.RedirectChecker
import de.danoeh.antennapod.net.common.UrlChecker
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager
import de.danoeh.antennapod.net.sync.gpoddernet.GpodnetService
import de.danoeh.antennapod.net.sync.nextcloud.NextcloudSyncService
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction
import de.danoeh.antennapod.net.sync.serviceinterface.ISyncService
import de.danoeh.antennapod.net.sync.serviceinterface.SyncServiceException
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationProvider
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.storage.database.FeedDatabaseWriter
import de.danoeh.antennapod.storage.database.LongList
import de.danoeh.antennapod.storage.preferences.SynchronizationCredentials
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.notifications.NotificationUtils
import java.util.Collections
import org.apache.commons.lang3.StringUtils
import org.greenrobot.eventbus.EventBus

class SyncService(context: Context, params: WorkerParameters) : Worker(context, params) {

    private val synchronizationQueueStorage: SynchronizationQueueStorage = SynchronizationQueueStorage(context)

    override fun doWork(): Result {
        val activeSyncProvider = getActiveSyncProvider() ?: return Result.success()

        if (currentlyActive) {
            return Result.success()
        }
        currentlyActive = true
        SynchronizationSettings.updateLastSynchronizationAttempt()
        try {
            activeSyncProvider.login()
            syncSubscriptions(activeSyncProvider)
            waitForDownloadServiceCompleted()
            if (someFeedWasNotRefreshedYet()) {
                // Note that this service might get called several times before the FeedUpdate completes
                Log.d(TAG, "Found new subscriptions. Need to refresh them before syncing episode actions")
                EventBus.getDefault().postSticky(SyncServiceEvent(R.string.sync_status_wait_for_downloads))
                // FeedUpdateManager.getInstance() is nullable (net:download:service-interface, M10);
                // Java dereferences it unguarded here today (D8 row 12).
                FeedUpdateManager.getInstance()!!.runOnce(applicationContext)
                return Result.success()
            }
            syncEpisodeActions(activeSyncProvider)
            activeSyncProvider.logout()
            clearErrorNotifications()
            EventBus.getDefault().postSticky(SyncServiceEvent(R.string.sync_status_success))
            SynchronizationSettings.setLastSynchronizationAttemptSuccess(true)
            return Result.success()
        } catch (e: Exception) {
            EventBus.getDefault().postSticky(SyncServiceEvent(R.string.sync_status_error))
            SynchronizationSettings.setLastSynchronizationAttemptSuccess(false)
            Log.e(TAG, Log.getStackTraceString(e))

            if (e is SyncServiceException) {
                if (runAttemptCount % 3 == 2) {
                    // Do not spam users with notification and retry before notifying
                    updateErrorNotification(e)
                }
                return Result.retry()
            } else {
                updateErrorNotification(e)
                return Result.failure()
            }
        } finally {
            currentlyActive = false
        }
    }

    private fun waitForDownloadServiceCompleted() {
        EventBus.getDefault().postSticky(SyncServiceEvent(R.string.sync_status_wait_for_downloads))
        try {
            while (true) {
                val event = EventBus.getDefault().getStickyEvent(FeedUpdateRunningEvent::class.java)
                if (event == null || !event.isFeedUpdateRunning) {
                    return
                }
                //noinspection BusyWait
                Thread.sleep(1000)
            }
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    private fun someFeedWasNotRefreshedYet(): Boolean {
        for (feed in DBReader.getFeedList()) {
            // preferences is unguarded in Java too (Feed.preferences is FeedPreferences?, D8 row 13).
            if (feed.preferences!!.getKeepUpdated() && feed.lastRefreshAttempt == 0L) {
                return true
            }
        }
        return false
    }

    private fun syncSubscriptions(syncServiceImpl: ISyncService) {
        val lastSync = SynchronizationSettings.getLastSubscriptionSynchronizationTimestamp()
        EventBus.getDefault().postSticky(SyncServiceEvent(R.string.sync_status_subscriptions))
        val localSubscriptions: MutableList<String> = DBReader.getFeedListDownloadUrls(true)
        val subscriptionChanges = syncServiceImpl.getSubscriptionChanges(lastSync)
        var newTimeStamp = subscriptionChanges.timestamp

        val queuedRemovedFeeds: MutableList<String> = synchronizationQueueStorage.getQueuedRemovedFeeds()
        var queuedAddedFeeds: MutableList<String> = synchronizationQueueStorage.getQueuedAddedFeeds()

        Log.d(TAG, "Downloaded subscription changes: $subscriptionChanges")
        for (downloadUrl in subscriptionChanges.added) {
            if (!downloadUrl.startsWith("http")) { // Also matches https
                Log.d(TAG, "Skipping url: $downloadUrl")
                continue
            } else if (UrlChecker.containsUrl(localSubscriptions, downloadUrl) ||
                queuedRemovedFeeds.contains(downloadUrl)
            ) {
                continue
            }
            val redirectedUrl = RedirectChecker.getNewUrlIfPermanentRedirect(downloadUrl)
            if (redirectedUrl != null &&
                (
                    UrlChecker.containsUrl(localSubscriptions, redirectedUrl) ||
                        queuedRemovedFeeds.contains(redirectedUrl)
                    )
            ) {
                continue
            }
            // If the creator redirected their feed incorrectly, we still try to avoid duplicated subscriptions
            val finalUrl = RedirectChecker.getFinalUrl(downloadUrl)
            if (UrlChecker.containsUrl(localSubscriptions, finalUrl) ||
                queuedRemovedFeeds.contains(finalUrl)
            ) {
                continue
            }

            val feed = Feed(downloadUrl, null, "Unknown podcast")
            feed.items = Collections.emptyList()
            FeedDatabaseWriter.updateFeed(applicationContext, feed, false)
        }

        // remove subscription if not just subscribed (again)
        for (downloadUrl in subscriptionChanges.removed) {
            if (!queuedAddedFeeds.contains(downloadUrl)) {
                DBWriter.removeFeedWithDownloadUrl(applicationContext, downloadUrl)
            }
        }

        if (lastSync == 0L) {
            Log.d(TAG, "First sync. Adding all local subscriptions.")
            queuedAddedFeeds = localSubscriptions
        }

        queuedAddedFeeds.removeAll(subscriptionChanges.added)
        queuedRemovedFeeds.removeAll(subscriptionChanges.removed)

        if (queuedAddedFeeds.isEmpty() && queuedRemovedFeeds.isEmpty()) {
            Log.d(TAG, "No feeds to add or remove from server")
            synchronizationQueueStorage.clearFeedQueues()
        } else {
            Log.d(TAG, "Added: ${StringUtils.join(queuedAddedFeeds, ", ")}")
            Log.d(TAG, "Removed: ${StringUtils.join(queuedRemovedFeeds, ", ")}")

            LockingAsyncExecutor.lock()
            try {
                val uploadResponse = syncServiceImpl
                    .uploadSubscriptionChanges(queuedAddedFeeds, queuedRemovedFeeds)
                synchronizationQueueStorage.clearFeedQueues()
                newTimeStamp = uploadResponse.timestamp
            } catch (exception: SyncServiceException) {
                synchronizationQueueStorage.removeLegacyConflictingFeedEntries(localSubscriptions)
                throw exception
            } finally {
                LockingAsyncExecutor.unlock()
            }
        }
        SynchronizationSettings.setLastSubscriptionSynchronizationAttemptTimestamp(newTimeStamp)
    }

    private fun syncEpisodeActions(syncServiceImpl: ISyncService) {
        val lastSync = SynchronizationSettings.getLastEpisodeActionSynchronizationTimestamp()
        EventBus.getDefault().postSticky(SyncServiceEvent(R.string.sync_status_episodes_download))
        val getResponse = syncServiceImpl.getEpisodeActionChanges(lastSync)
        var newTimeStamp = getResponse.timestamp
        val remoteActions = getResponse.episodeActions
        processEpisodeActions(remoteActions)

        // upload local actions
        EventBus.getDefault().postSticky(SyncServiceEvent(R.string.sync_status_episodes_upload))
        val queuedEpisodeActions: MutableList<EpisodeAction?> = synchronizationQueueStorage.getQueuedEpisodeActions()
        if (lastSync == 0L) {
            EventBus.getDefault().postSticky(SyncServiceEvent(R.string.sync_status_upload_played))
            val readItems = DBReader.getEpisodes(
                0,
                Int.MAX_VALUE,
                FeedItemFilter(FeedItemFilter.PLAYED),
                SortOrder.DATE_NEW_OLD
            )
            Log.d(TAG, "First sync. Upload state for all " + readItems.size + " played episodes")
            for (item in readItems) {
                val media = item.media ?: continue
                val played = EpisodeAction.Builder(item, EpisodeAction.PLAY)
                    .currentTimestamp()
                    .started(media.duration / 1000)
                    .position(media.duration / 1000)
                    .total(media.duration / 1000)
                    .build()
                queuedEpisodeActions.add(played)
            }
        }
        if (!queuedEpisodeActions.isEmpty()) {
            LockingAsyncExecutor.lock()
            try {
                Log.d(
                    TAG,
                    "Uploading ${queuedEpisodeActions.size} actions: " +
                        StringUtils.join(queuedEpisodeActions, ", ")
                )
                // D3: getQueuedEpisodeActions() is honestly ArrayList<EpisodeAction?> (a corrupt
                // stored entry parses to null), but ISyncService.uploadEpisodeActions declares a
                // non-null element list (M11). This cast inspects no element -- Kotlin compiles a
                // generic cast to a bare CHECKCAST -- so it is the only option that neither drops
                // a malformed entry nor crashes earlier than Java does.
                @Suppress("UNCHECKED_CAST")
                val postResponse = syncServiceImpl.uploadEpisodeActions(queuedEpisodeActions as List<EpisodeAction>)
                newTimeStamp = postResponse.timestamp
                Log.d(TAG, "Upload episode response: $postResponse")
                synchronizationQueueStorage.clearEpisodeActionQueue()
            } finally {
                LockingAsyncExecutor.unlock()
            }
        }
        SynchronizationSettings.setLastEpisodeActionSynchronizationAttemptTimestamp(newTimeStamp)
    }

    @Synchronized
    private fun processEpisodeActions(remoteActions: List<EpisodeAction>) {
        Log.d(TAG, "Processing " + remoteActions.size + " actions")
        if (remoteActions.isEmpty()) {
            return
        }

        val playActionsToUpdate = EpisodeActionFilter.getRemoteActionsOverridingLocalActions(
            remoteActions,
            synchronizationQueueStorage.getQueuedEpisodeActions()
        )
        val queueToBeRemoved = LongList()
        val updatedItems: MutableList<FeedItem> = ArrayList()
        for (action in playActionsToUpdate.values) {
            val guid = if (GuidValidator.isValidGuid(action.guid)) action.guid else null
            val feedItem = DBReader.getFeedItemByGuidOrEpisodeUrl(guid, action.episode)
            if (feedItem == null) {
                Log.i(TAG, "Unknown feed item: $action")
                continue
            }
            if (feedItem.media == null) {
                Log.i(TAG, "Feed item has no media: $action")
                continue
            }
            // media is a cross-module var, so the null check above grants no smart cast; Java
            // re-reads the getter here too, and this is that same re-read (D8 row 14).
            val media = feedItem.media!!
            media.position = action.position * 1000
            val smartMarkAsPlayedSecs = UserPreferences.getSmartMarkAsPlayedSecs()
            val almostEnded = media.duration > 0 &&
                media.position >= media.duration - smartMarkAsPlayedSecs * 1000
            if (almostEnded) {
                Log.d(TAG, "Marking as played: $action")
                feedItem.isPlayed = true
                media.position = 0
                queueToBeRemoved.add(feedItem.id)
            } else {
                Log.d(TAG, "Setting position: $action")
            }
            updatedItems.add(feedItem)
        }
        DBWriter.removeQueueItem(applicationContext, false, *queueToBeRemoved.toArray())
        DBReader.loadFeedDataOfFeedItemList(updatedItems)
        DBWriter.setItemList(updatedItems)
    }

    private fun clearErrorNotifications() {
        // getSystemService is @Nullable in the SDK; Java's cast throws NPE one statement later at
        // nm.cancel(...) -- `as NotificationManager?` + `!!` here reproduces that same exception
        // at the same site, rather than moving it earlier with an idiomatic `as NotificationManager` (D8).
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        nm!!.cancel(R.id.notification_gpodnet_sync_error)
        nm.cancel(R.id.notification_gpodnet_sync_autherror)
    }

    private fun updateErrorNotification(exception: Exception) {
        Log.d(TAG, "Posting sync error notification")
        val description = applicationContext.getString(R.string.gpodnetsync_error_descr) + exception.message

        if (!UserPreferences.gpodnetNotificationsEnabled()) {
            Log.d(TAG, "Skipping sync error notification because of user setting")
            return
        }
        if (EventBus.getDefault().hasSubscriberForEvent(MessageEvent::class.java)) {
            EventBus.getDefault().post(MessageEvent(description))
            return
        }

        val intent = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            R.id.pending_intent_sync_error,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, NotificationUtils.CHANNEL_ID_SYNC_ERROR)
            .setContentTitle(applicationContext.getString(R.string.gpodnetsync_error_title))
            .setContentText(description)
            .setStyle(NotificationCompat.BigTextStyle().bigText(description))
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.drawable.ic_notification_sync_error)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        // Same reasoning as clearErrorNotifications: `as NotificationManager?` + `!!` here, not an
        // idiomatic `as NotificationManager`, preserves the case where the service lookup returns
        // null AND the permission is denied -- Java never throws at all on that path (D8 row 17).
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            nm!!.notify(R.id.notification_gpodnet_sync_error, notification)
        }
    }

    private fun getActiveSyncProvider(): ISyncService? {
        val selectedSyncProviderKey = SynchronizationSettings.getSelectedSyncProviderKey()
        val selectedService = SynchronizationProvider.fromIdentifier(selectedSyncProviderKey)
            ?: return null

        return when (selectedService) {
            SynchronizationProvider.GPODDER_NET -> GpodnetService(
                AntennapodHttpClient.getHttpClient(),
                SynchronizationCredentials.getHosturl(),
                SynchronizationCredentials.getDeviceId(),
                SynchronizationCredentials.getUsername(),
                SynchronizationCredentials.getPassword()
            )
            SynchronizationProvider.NEXTCLOUD_GPODDER -> NextcloudSyncService(
                AntennapodHttpClient.getHttpClient(),
                SynchronizationCredentials.getHosturl(),
                SynchronizationCredentials.getUsername(),
                SynchronizationCredentials.getPassword()
            )
            else -> null
        }
    }

    companion object {
        const val TAG = "SyncService"

        private var currentlyActive = false

        internal fun isCurrentlyActive(): Boolean {
            return currentlyActive
        }
    }
}
