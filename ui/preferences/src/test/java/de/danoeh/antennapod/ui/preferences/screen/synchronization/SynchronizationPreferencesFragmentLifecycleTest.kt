package de.danoeh.antennapod.ui.preferences.screen.synchronization

import android.content.Context
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import de.danoeh.antennapod.event.SyncServiceEvent
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationProvider
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue
import de.danoeh.antennapod.storage.preferences.SynchronizationCredentials
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.preferences.R
import org.greenrobot.eventbus.EventBus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SynchronizationPreferencesFragmentLifecycleTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        SynchronizationSettings.init(context)
        SynchronizationCredentials.init(context)
        UserPreferences.init(context)
        SynchronizationQueue.instance = RecordingSynchronizationQueue()
        EventBus.getDefault().removeStickyEvent(SyncServiceEvent::class.java)
    }

    @After
    fun tearDown() {
        SynchronizationQueue.instance = null
        EventBus.getDefault().removeStickyEvent(SyncServiceEvent::class.java)
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this)
        }
    }

    private fun attach(activity: AppCompatActivity): SynchronizationPreferencesFragment {
        val fragment = SynchronizationPreferencesFragment()
        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, fragment, "sync-settings")
            .commitNow()
        shadowOf(Looper.getMainLooper()).idle()
        return fragment
    }

    @Test
    fun testStickyEventReplaysOnStart() {
        SynchronizationSettings.setSelectedSyncProvider(SynchronizationProvider.GPODDER_NET.identifier)
        SynchronizationCredentials.setUsername("someone")
        SynchronizationSettings.setLastSynchronizationAttemptSuccess(true)
        SynchronizationSettings.updateLastSynchronizationAttempt()

        EventBus.getDefault().postSticky(SyncServiceEvent(R.string.sync_status_started))

        val activity = Robolectric.buildActivity(SyncSettingsTestHost::class.java).setup().get()
        attach(activity)

        assertEquals(context.getString(R.string.sync_status_started), activity.supportActionBar!!.subtitle)
    }

    @Test
    fun testSyncEventIgnoredWhenNotConnected() {
        val activity = Robolectric.buildActivity(SyncSettingsTestHost::class.java).setup().get()
        attach(activity)
        assertNull(activity.supportActionBar!!.subtitle)

        EventBus.getDefault().post(SyncServiceEvent(R.string.sync_status_error))
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(activity.supportActionBar!!.subtitle)
    }

    @Test
    fun testSubtitleBranchesOnMessageResId() {
        SynchronizationSettings.setSelectedSyncProvider(SynchronizationProvider.GPODDER_NET.identifier)
        SynchronizationCredentials.setUsername("someone")
        SynchronizationSettings.setLastSynchronizationAttemptSuccess(true)
        SynchronizationSettings.updateLastSynchronizationAttempt()

        val activity = Robolectric.buildActivity(SyncSettingsTestHost::class.java).setup().get()
        attach(activity)

        EventBus.getDefault().post(SyncServiceEvent(R.string.sync_status_success))
        shadowOf(Looper.getMainLooper()).idle()
        val successSubtitle = activity.supportActionBar!!.subtitle.toString()
        assertTrue(successSubtitle.contains(context.getString(R.string.gpodnetsync_pref_report_successful)))

        EventBus.getDefault().post(SyncServiceEvent(R.string.sync_status_started))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(context.getString(R.string.sync_status_started), activity.supportActionBar!!.subtitle)
        assertNotEquals(successSubtitle, activity.supportActionBar!!.subtitle)
    }

    @Test
    fun testOnStartSetsTitleAndOnStopSetsEmptySubtitleWhereasDisconnectedSetsNull() {
        val controller = Robolectric.buildActivity(SyncSettingsTestHost::class.java)
        val activity = controller.setup().get()
        attach(activity)

        assertEquals(context.getString(R.string.synchronization_pref), activity.supportActionBar!!.title)
        assertNull(activity.supportActionBar!!.subtitle)

        controller.pause().stop()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("", activity.supportActionBar!!.subtitle)
    }
}
