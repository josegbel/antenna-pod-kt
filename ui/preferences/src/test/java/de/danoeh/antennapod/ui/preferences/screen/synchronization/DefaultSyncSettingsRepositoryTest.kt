package de.danoeh.antennapod.ui.preferences.screen.synchronization

import android.content.Context
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DefaultSyncSettingsRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: DefaultSyncSettingsRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        SynchronizationSettings.init(context)
        repository = DefaultSyncSettingsRepository()
    }

    @After
    fun tearDown() {
        SynchronizationQueue.instance = null
    }

    @Test
    fun testReadsDelegateToSynchronizationSettingsOnEveryCall() {
        SynchronizationSettings.setSelectedSyncProvider(null)
        SynchronizationSettings.setLastSynchronizationAttemptSuccess(false)
        SynchronizationSettings.resetTimestamps()

        assertFalse(repository.isProviderConnected())
        assertFalse(repository.isLastSyncSuccessful())
        assertEquals(0L, repository.lastSyncAttempt())

        SynchronizationSettings.setSelectedSyncProvider("gpodder")
        SynchronizationSettings.setLastSynchronizationAttemptSuccess(true)
        val prefs = context.getSharedPreferences("synchronization", Context.MODE_PRIVATE)
        prefs.edit().putLong("last_sync_attempt_timestamp", 1234L).apply()

        assertTrue(repository.isProviderConnected())
        assertTrue(repository.isLastSyncSuccessful())
        assertEquals(1234L, repository.lastSyncAttempt())
    }

    @Test
    fun testQueueCommandsDelegateToWhicheverQueueIsInstalledAtCallTime() {
        val queueA = RecordingSynchronizationQueue()
        SynchronizationQueue.instance = queueA
        repository.syncImmediately()

        val queueB = RecordingSynchronizationQueue()
        SynchronizationQueue.instance = queueB
        repository.fullSync()
        repository.clearSyncQueue()

        assertEquals(listOf("syncImmediately"), queueA.calls)
        assertEquals(listOf("fullSync", "clear"), queueB.calls)
    }

    @Test
    fun testQueueCommandThrowsNullPointerExceptionWhenNoQueueIsInstalled() {
        SynchronizationQueue.instance = null

        assertThrows(NullPointerException::class.java) { repository.syncImmediately() }
    }
}
