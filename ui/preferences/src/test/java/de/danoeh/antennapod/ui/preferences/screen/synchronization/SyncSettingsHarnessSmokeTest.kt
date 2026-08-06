package de.danoeh.antennapod.ui.preferences.screen.synchronization

import android.content.Context
import android.widget.ViewFlipper
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import de.danoeh.antennapod.net.common.AntennapodHttpClient
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue
import de.danoeh.antennapod.storage.preferences.SynchronizationCredentials
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings
import de.danoeh.antennapod.storage.preferences.UserPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowDialog

/**
 * D5's five proof-obligation tests. This is the first Fragment/Activity-driving test anywhere in
 * this repo (D3), so each test proves one specific harness mechanism works before any behavior
 * is characterized on top of it. Which of D5's fallbacks were needed is recorded in Implementation
 * Notes, not here.
 */
@RunWith(RobolectricTestRunner::class)
class SyncSettingsHarnessSmokeTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        SynchronizationSettings.init(context)
        SynchronizationCredentials.init(context)
        UserPreferences.init(context)
        SynchronizationQueue.instance = RecordingSynchronizationQueue()
    }

    @After
    fun tearDown() {
        SynchronizationQueue.instance = null
    }

    @Test
    fun testHostActivityBuilds() {
        val controller = Robolectric.buildActivity(SyncSettingsTestHost::class.java)
        val activity = controller.setup().get()
        assertNotNull(activity)
        assertTrue(activity is AppCompatActivity)
    }

    @Test
    fun testSynchronizationPreferencesFragmentAttachesAndResolvesAllKeys() {
        val activity = Robolectric.buildActivity(SyncSettingsTestHost::class.java).setup().get()
        val fragment = SynchronizationPreferencesFragment()
        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, fragment, "sync-settings")
            .commitNow()

        val keys = listOf(
            "preference_synchronization_description",
            "pref_gpodnet_setlogin_information",
            "pref_synchronization_sync",
            "pref_synchronization_force_full_sync",
            "pref_synchronization_logout"
        )
        for (key in keys) {
            val preference: Preference? = fragment.findPreference(key)
            assertNotNull("expected to resolve preference key $key", preference)
        }
    }

    @Test
    fun testAuthenticationDialogShowsAndIsRetrievableViaShadowDialog() {
        val activity = Robolectric.buildActivity(SyncSettingsTestHost::class.java).setup().get()
        val dialog = object : AuthenticationDialog(activity, android.R.string.ok, false, null, null) {
            override fun onConfirmed(username: String, password: String) {}
        }
        dialog.show()

        val latest = ShadowDialog.getLatestDialog()
        assertNotNull(latest)
    }

    @Test
    fun testGpodderAuthenticationFragmentOnCreateDialogCompletesAtDisplayedChildZero() {
        val activity = Robolectric.buildActivity(SyncSettingsTestHost::class.java).setup().get()
        val fragment = GpodderAuthenticationFragment()
        fragment.showNow(activity.supportFragmentManager, GpodderAuthenticationFragment.TAG)

        val dialog = fragment.dialog
        assertNotNull(dialog)
        val viewFlipper = dialog!!.findViewById<ViewFlipper>(de.danoeh.antennapod.ui.preferences.R.id.viewflipper)
        assertNotNull(viewFlipper)
        assertEquals(0, viewFlipper.displayedChild)
    }

    @Test
    fun testNextcloudAuthenticationFragmentOnCreateDialogCompletes() {
        val activity = Robolectric.buildActivity(SyncSettingsTestHost::class.java).setup().get()
        val fragment = NextcloudAuthenticationFragment()
        fragment.showNow(activity.supportFragmentManager, NextcloudAuthenticationFragment.TAG)

        assertNotNull(fragment.dialog)
    }

    @Test
    fun testAntennapodHttpClientConstructionUnderRobolectric() {
        // D5's fourth proof obligation, recorded as observed fact per AC2, not assumed: it is
        // NOT constructible under default Robolectric setup. getHttpClient() -> newBuilder()
        // constructs an okhttp3.Cache(cacheDirectory, ...) with a null cacheDirectory (nothing in
        // this test process calls AntennapodHttpClient.setCacheDirectory), and Cache's constructor
        // rejects a null directory. This confirms D5's row-4 fallback is required for gap 14's test.
        org.junit.Assert.assertThrows(NullPointerException::class.java) {
            AntennapodHttpClient.getHttpClient()
        }
    }
}
