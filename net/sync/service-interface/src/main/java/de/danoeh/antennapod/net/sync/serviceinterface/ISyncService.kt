package de.danoeh.antennapod.net.sync.serviceinterface

interface ISyncService {

    @Throws(SyncServiceException::class)
    fun login()

    @Throws(SyncServiceException::class)
    fun getSubscriptionChanges(lastSync: Long): SubscriptionChanges

    @Throws(SyncServiceException::class)
    @JvmSuppressWildcards
    fun uploadSubscriptionChanges(addedFeeds: List<String>, removedFeeds: List<String>): UploadChangesResponse

    @Throws(SyncServiceException::class)
    fun getEpisodeActionChanges(lastSync: Long): EpisodeActionChanges

    @Throws(SyncServiceException::class)
    @JvmSuppressWildcards
    fun uploadEpisodeActions(queuedEpisodeActions: List<EpisodeAction>): UploadChangesResponse

    @Throws(SyncServiceException::class)
    fun logout()
}
