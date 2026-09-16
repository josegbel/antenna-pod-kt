package de.danoeh.antennapod.ui.preferences.screen.synchronization

import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationProvider
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue
import de.danoeh.antennapod.storage.preferences.SynchronizationCredentials
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.preferences.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val KEY_SYNC = "pref_synchronization_sync"

@HiltAndroidTest
@UninstallModules(SyncSettingsModule::class)
@Config(application = HiltTestApplication::class)
@RunWith(RobolectricTestRunner::class)
class SyncSettingsHiltGraphTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val syncSettingsRepository: SyncSettingsRepository = RecordingSyncSettingsRepository()

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        SynchronizationSettings.init(context)
        SynchronizationCredentials.init(context)
        UserPreferences.init(context)
        SynchronizationQueue.instance = RecordingSynchronizationQueue()
    }

    @After
    fun tearDown() {
        SynchronizationQueue.instance = null
    }

    private fun attachFragment(): Pair<AppCompatActivity, SynchronizationPreferencesFragment> {
        val activity = Robolectric.buildActivity(SyncSettingsHiltTestHost::class.java).setup().get()
        val fragment = SynchronizationPreferencesFragment()
        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, fragment, "sync-settings")
            .commitNow()
        shadowOf(Looper.getMainLooper()).idle()
        return activity to fragment
    }

    @Test
    fun testFragmentFieldInjectionReceivesTheBoundRepository() {
        SynchronizationSettings.setSelectedSyncProvider(SynchronizationProvider.GPODDER_NET.identifier)
        val (_, fragment) = attachFragment()
        val fake = syncSettingsRepository as RecordingSyncSettingsRepository
        val queueOnGlobal = SynchronizationQueue.instance as RecordingSynchronizationQueue
        fake.calls.clear()

        fragment.findPreference<Preference>(KEY_SYNC)!!.performClick()

        assertEquals(listOf("syncImmediately"), fake.calls)
        assertTrue(queueOnGlobal.calls.isEmpty())
    }

    @Test
    fun testViewModelIsConstructedByHiltWithTheBoundRepository() {
        val fake = syncSettingsRepository as RecordingSyncSettingsRepository
        fake.providerConnected = true
        fake.lastSyncSuccessful = true
        fake.lastSyncAttemptValue = System.currentTimeMillis()

        val (activity, _) = attachFragment()

        val context = RuntimeEnvironment.getApplication()
        val expectedSubstring = context.getString(R.string.gpodnetsync_pref_report_successful)
        val subtitle = activity.supportActionBar!!.subtitle?.toString()

        assertTrue(
            "Expected subtitle to contain '$expectedSubstring' from the bound fake's report, was '$subtitle'",
            subtitle != null && subtitle.contains(expectedSubstring)
        )
    }
}

private class RecordingSyncSettingsRepository : SyncSettingsRepository {
    val calls = mutableListOf<String>()

    var providerConnected = false
    var lastSyncSuccessful = false
    var lastSyncAttemptValue = 0L

    override fun isProviderConnected(): Boolean {
        calls.add("isProviderConnected")
        return providerConnected
    }

    override fun isLastSyncSuccessful(): Boolean {
        calls.add("isLastSyncSuccessful")
        return lastSyncSuccessful
    }

    override fun lastSyncAttempt(): Long {
        calls.add("lastSyncAttempt")
        return lastSyncAttemptValue
    }

    override fun syncImmediately() {
        calls.add("syncImmediately")
    }

    override fun fullSync() {
        calls.add("fullSync")
    }

    override fun clearSyncQueue() {
        calls.add("clearSyncQueue")
    }
}
