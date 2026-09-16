package de.danoeh.antennapod.ui.preferences.screen.synchronization

import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentFactory
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.google.android.material.transition.MaterialSharedAxis
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationProvider
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue
import de.danoeh.antennapod.storage.preferences.SynchronizationCredentials
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.common.ThemeUtils
import de.danoeh.antennapod.ui.preferences.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

private const val KEY_SYNC = "pref_synchronization_sync"
private const val KEY_FORCE_FULL_SYNC = "pref_synchronization_force_full_sync"
private const val KEY_LOGOUT = "pref_synchronization_logout"

@RunWith(RobolectricTestRunner::class)
class SyncSettingsSeamCharacterizationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        SynchronizationSettings.init(context)
        SynchronizationCredentials.init(context)
        UserPreferences.init(context)
    }

    @After
    fun tearDown() {
        SynchronizationQueue.instance = null
    }

    private fun attachFragment(): Pair<AppCompatActivity, SynchronizationPreferencesFragment> {
        val activity = Robolectric.buildActivity(SyncSettingsTestHost::class.java).setup().get()
        val fragment = SynchronizationPreferencesFragment()
        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, fragment, "sync-settings")
            .commitNow()
        return activity to fragment
    }

    @Test
    fun testQueueIsResolvedFromTheGlobalAtEachClickNotCachedAtAttach() {
        SynchronizationSettings.setSelectedSyncProvider(SynchronizationProvider.GPODDER_NET.identifier)
        val queueA = RecordingSynchronizationQueue()
        SynchronizationQueue.instance = queueA
        val (_, fragment) = attachFragment()

        fragment.findPreference<Preference>(KEY_SYNC)!!.performClick()
        assertEquals(listOf("syncImmediately"), queueA.calls)

        val queueB = RecordingSynchronizationQueue()
        SynchronizationQueue.instance = queueB

        fragment.findPreference<Preference>(KEY_FORCE_FULL_SYNC)!!.performClick()
        assertEquals(listOf("fullSync"), queueB.calls)
        assertEquals(listOf("syncImmediately"), queueA.calls)
    }

    @Test
    fun testLogoutRowClearsCredentialsFlipsGpodnetNotificationsAndThenClearsTheQueue() {
        SynchronizationSettings.setSelectedSyncProvider(SynchronizationProvider.GPODDER_NET.identifier)
        SynchronizationCredentials.setUsername("someone")
        SynchronizationCredentials.setPassword("secret")
        SynchronizationCredentials.setDeviceId("device1")
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean("pref_gpodnet_notifications", false)
            .apply()
        assertFalse(UserPreferences.getGpodnetNotificationsEnabledRaw())

        val recordingQueue = RecordingSynchronizationQueue()
        SynchronizationQueue.instance = recordingQueue
        val (_, fragment) = attachFragment()

        var usernameAtQueueClearTime: String? = "unset"
        recordingQueue.onCall = { name ->
            if (name == "clear") {
                usernameAtQueueClearTime = SynchronizationCredentials.getUsername()
            }
        }

        fragment.findPreference<Preference>(KEY_LOGOUT)!!.performClick()

        assertNull(usernameAtQueueClearTime)
        assertNull(SynchronizationCredentials.getUsername())
        assertNull(SynchronizationCredentials.getPassword())
        assertNull(SynchronizationCredentials.getDeviceId())
        assertTrue(UserPreferences.getGpodnetNotificationsEnabledRaw())
        assertEquals(listOf("clear"), recordingQueue.calls)
    }

    @Test
    fun testSharedAxisTransitionsAndSurfaceBackgroundAreAppliedToTheAttachedFragment() {
        val (_, fragment) = attachFragment()

        assertTrue(fragment.enterTransition is MaterialSharedAxis)
        assertTrue(fragment.returnTransition is MaterialSharedAxis)
        assertTrue(fragment.exitTransition is MaterialSharedAxis)
        assertTrue(fragment.reenterTransition is MaterialSharedAxis)

        val background = fragment.requireView().background
        assertNotNull(background)
        assertTrue(background is ColorDrawable)
        assertEquals(
            ThemeUtils.getColorFromAttr(fragment.requireContext(), R.attr.colorSurface),
            (background as ColorDrawable).color
        )
    }

    @Test
    fun testFragmentRemainsInstantiableByFragmentFactory() {
        val classLoader = SynchronizationPreferencesFragment::class.java.classLoader!!
        val className = SynchronizationPreferencesFragment::class.java.name

        val instance = FragmentFactory().instantiate(classLoader, className)

        assertTrue(instance is SynchronizationPreferencesFragment)
    }
}
