package de.danoeh.antennapod.ui.preferences.screen.synchronization

import android.os.Looper
import de.danoeh.antennapod.event.SyncServiceEvent
import de.danoeh.antennapod.ui.preferences.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SynchronizationPreferencesViewModelTest {

    private val jobs = mutableListOf<Job>()

    @Before
    fun setUp() {
        EventBus.getDefault().removeStickyEvent(SyncServiceEvent::class.java)
    }

    @After
    fun tearDown() {
        jobs.forEach { it.cancel() }
        EventBus.getDefault().removeStickyEvent(SyncServiceEvent::class.java)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun startCollector(viewModel: SynchronizationPreferencesViewModel): Job {
        val job = CoroutineScope(Dispatchers.Main.immediate).launch { viewModel.syncStatus.collect {} }
        jobs.add(job)
        idle()
        return job
    }

    @Test
    fun testStickyEventPostedBeforeCollectionIsReplayedIntoSyncStatus() {
        EventBus.getDefault().postSticky(SyncServiceEvent(R.string.sync_status_started))
        val viewModel = SynchronizationPreferencesViewModel()

        startCollector(viewModel)

        assertEquals(R.string.sync_status_started, viewModel.syncStatus.value?.messageResId)
    }

    @Test
    fun testSyncStatusStartsNullWhenNoStickyEventExists() {
        val viewModel = SynchronizationPreferencesViewModel()

        startCollector(viewModel)

        assertNull(viewModel.syncStatus.value)
    }

    @Test
    fun testConsecutiveEventsWithSameMessageResIdAreBothDelivered() {
        val viewModel = SynchronizationPreferencesViewModel()
        val seen = mutableListOf<SyncServiceEvent?>()
        val job = CoroutineScope(Dispatchers.Main.immediate).launch {
            viewModel.syncStatus.collect { seen += it }
        }
        jobs.add(job)
        idle()

        EventBus.getDefault().post(SyncServiceEvent(R.string.sync_status_started))
        idle()
        EventBus.getDefault().post(SyncServiceEvent(R.string.sync_status_started))
        idle()
        job.cancel()

        assertEquals(3, seen.size)
        assertNull(seen[0])
        assertEquals(R.string.sync_status_started, seen[1]?.messageResId)
        assertEquals(R.string.sync_status_started, seen[2]?.messageResId)
        assertNotSame(seen[1], seen[2])
    }

    @Test
    fun testCollectorCancellationUnregistersFromEventBus() {
        val viewModel = SynchronizationPreferencesViewModel()
        val before = EventBus.getDefault().hasSubscriberForEvent(SyncServiceEvent::class.java)

        val job = startCollector(viewModel)
        assertTrue(EventBus.getDefault().hasSubscriberForEvent(SyncServiceEvent::class.java))

        job.cancel()
        idle()

        assertEquals(before, EventBus.getDefault().hasSubscriberForEvent(SyncServiceEvent::class.java))
    }

    @Test
    fun testEventsPostedWhileNotCollectingAreSeenOnlyIfSticky() {
        val viewModel = SynchronizationPreferencesViewModel()

        EventBus.getDefault().post(SyncServiceEvent(R.string.sync_status_started))
        idle()
        val firstJob = startCollector(viewModel)
        assertNull(viewModel.syncStatus.value)
        firstJob.cancel()
        idle()

        EventBus.getDefault().postSticky(SyncServiceEvent(R.string.sync_status_success))
        idle()
        startCollector(viewModel)

        assertEquals(R.string.sync_status_success, viewModel.syncStatus.value?.messageResId)
    }
}
