package de.danoeh.antennapod.ui.preferences.screen.synchronization

import android.content.Context
import android.os.Looper
import android.text.Spanned
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationProvider
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue
import de.danoeh.antennapod.storage.preferences.SynchronizationCredentials
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.preferences.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog

private const val KEY_DESCRIPTION = "preference_synchronization_description"
private const val KEY_GPODNET_SETLOGIN = "pref_gpodnet_setlogin_information"
private const val KEY_SYNC = "pref_synchronization_sync"
private const val KEY_FORCE_FULL_SYNC = "pref_synchronization_force_full_sync"
private const val KEY_LOGOUT = "pref_synchronization_logout"

@RunWith(RobolectricTestRunner::class)
class SynchronizationPreferencesFragmentCharacterizationTest {

    private lateinit var context: Context
    private lateinit var recordingQueue: RecordingSynchronizationQueue

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        SynchronizationSettings.init(context)
        SynchronizationCredentials.init(context)
        UserPreferences.init(context)
        recordingQueue = RecordingSynchronizationQueue()
        SynchronizationQueue.instance = recordingQueue
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
    fun testLoggedOutScreenState() {
        val (_, fragment) = attachFragment()

        val header = fragment.findPreference<Preference>(KEY_DESCRIPTION)!!
        assertEquals(context.getString(R.string.synchronization_choose_title), header.title)
        assertEquals(context.getString(R.string.synchronization_summary_unchoosen), header.summary)
        assertNull(header.icon)

        val gpodnetSetLogin = fragment.findPreference<Preference>(KEY_GPODNET_SETLOGIN)!!
        val sync = fragment.findPreference<Preference>(KEY_SYNC)!!
        val forceFullSync = fragment.findPreference<Preference>(KEY_FORCE_FULL_SYNC)!!
        val logout = fragment.findPreference<Preference>(KEY_LOGOUT)!!
        assertFalse(gpodnetSetLogin.isEnabled)
        assertFalse(sync.isEnabled)
        assertFalse(forceFullSync.isEnabled)
        assertFalse(logout.isEnabled)
        assertNull(logout.summary)

        val before = ShadowDialog.getLatestDialog()
        header.performClick()
        val after = ShadowDialog.getLatestDialog()
        assertNotNull(after)
        assertTrue(before == null || before !== after)
    }

    @Test
    fun testLoggedInScreenState() {
        SynchronizationSettings.setSelectedSyncProvider(SynchronizationProvider.GPODDER_NET.identifier)
        SynchronizationCredentials.setUsername("someone")
        SynchronizationCredentials.setHosturl("https://gpodder.net")
        val (_, fragment) = attachFragment()

        val header = fragment.findPreference<Preference>(KEY_DESCRIPTION)!!
        assertEquals("", header.title)
        assertEquals(context.getString(R.string.gpodnet_description), header.summary)
        assertNotNull(header.icon)
        assertEquals(R.drawable.gpodder_icon, shadowOf(header.icon).createdFromResId)

        val gpodnetSetLogin = fragment.findPreference<Preference>(KEY_GPODNET_SETLOGIN)!!
        val sync = fragment.findPreference<Preference>(KEY_SYNC)!!
        val forceFullSync = fragment.findPreference<Preference>(KEY_FORCE_FULL_SYNC)!!
        val logout = fragment.findPreference<Preference>(KEY_LOGOUT)!!
        assertTrue(gpodnetSetLogin.isEnabled)
        assertTrue(sync.isEnabled)
        assertTrue(forceFullSync.isEnabled)
        assertTrue(logout.isEnabled)

        val before = ShadowDialog.getLatestDialog()
        header.performClick()
        val after = ShadowDialog.getLatestDialog()
        assertSame(before, after)
    }

    @Test
    fun testGpodnetRowVisibleOnlyForGpodderNet() {
        SynchronizationSettings.setSelectedSyncProvider(SynchronizationProvider.GPODDER_NET.identifier)
        val (_, gpodderFragment) = attachFragment()
        val gpodderRow = gpodderFragment.findPreference<Preference>(KEY_GPODNET_SETLOGIN)!!
        assertTrue(gpodderRow.isVisible)
        assertTrue(gpodderRow.isEnabled)

        SynchronizationSettings.setSelectedSyncProvider(SynchronizationProvider.NEXTCLOUD_GPODDER.identifier)
        val (_, nextcloudFragment) = attachFragment()
        val nextcloudRow = nextcloudFragment.findPreference<Preference>(KEY_GPODNET_SETLOGIN)!!
        assertFalse(nextcloudRow.isVisible)
        assertTrue(nextcloudRow.isEnabled)
    }

    @Test
    fun testLogoutSummaryIsHtmlParsedSpanned() {
        SynchronizationSettings.setSelectedSyncProvider(SynchronizationProvider.GPODDER_NET.identifier)
        SynchronizationCredentials.setUsername("someone")
        SynchronizationCredentials.setHosturl("https://gpodder.net")
        val (_, fragment) = attachFragment()

        val logout = fragment.findPreference<Preference>(KEY_LOGOUT)!!
        val summary = logout.summary
        assertNotNull(summary)
        assertTrue(summary is Spanned)
        val rawStringResource = context.getString(
            R.string.synchronization_login_status,
            "someone",
            "https://gpodder.net"
        )
        assertTrue(rawStringResource.contains("\n\n"))
        assertTrue(summary.toString().contains("someone"))
        assertTrue(summary.toString().contains("gpodder.net"))
    }

    @Test
    fun testUnrecognisedProviderKeyThrowsAfterClearingHeaderTitle() {
        SynchronizationSettings.setSelectedSyncProvider("SOME_STALE_UNRECOGNISED_KEY")

        val fragment = SynchronizationPreferencesFragment()
        assertThrows(NullPointerException::class.java) {
            val activity = Robolectric.buildActivity(SyncSettingsTestHost::class.java).setup().get()
            activity.supportFragmentManager.beginTransaction()
                .add(android.R.id.content, fragment, "sync-settings")
                .commitNow()
        }

        val header = fragment.findPreference<Preference>(KEY_DESCRIPTION)!!
        assertEquals("", header.title)
    }

    @Test
    fun testSetLoginDialogSeedsUsernameDisabledAndWritesPasswordOnly() {
        SynchronizationSettings.setSelectedSyncProvider(SynchronizationProvider.GPODDER_NET.identifier)
        SynchronizationCredentials.setUsername("existingUser")
        SynchronizationCredentials.setPassword("oldPassword")
        val (_, fragment) = attachFragment()

        val gpodnetSetLogin = fragment.findPreference<Preference>(KEY_GPODNET_SETLOGIN)!!
        gpodnetSetLogin.performClick()

        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull(dialog)
        val usernameField = dialog.findViewById<android.widget.EditText>(
            de.danoeh.antennapod.ui.preferences.R.id.usernameEditText
        )
        val passwordField = dialog.findViewById<android.widget.EditText>(
            de.danoeh.antennapod.ui.preferences.R.id.passwordEditText
        )
        assertFalse(usernameField.isEnabled)
        assertEquals("existingUser", usernameField.text.toString())

        passwordField.setText("newPassword")
        val alertDialog = dialog as androidx.appcompat.app.AlertDialog
        alertDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("newPassword", SynchronizationCredentials.getPassword())
        assertEquals("existingUser", SynchronizationCredentials.getUsername())
    }

    @Test
    fun testLogoutOrderingAndCrossClassSideEffect() {
        SynchronizationSettings.setSelectedSyncProvider(SynchronizationProvider.GPODDER_NET.identifier)
        SynchronizationCredentials.setUsername("someone")
        SynchronizationCredentials.setPassword("secret")
        SynchronizationCredentials.setDeviceId("device1")
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean("pref_gpodnet_notifications", false)
            .apply()
        assertFalse(UserPreferences.getGpodnetNotificationsEnabledRaw())

        val (_, fragment) = attachFragment()

        var usernameAtQueueClearTime: String? = "unset"
        var selectedProviderKeyAtQueueClearTime: String? = "unset"
        recordingQueue.onCall = { name ->
            if (name == "clear") {
                usernameAtQueueClearTime = SynchronizationCredentials.getUsername()
                selectedProviderKeyAtQueueClearTime = SynchronizationSettings.getSelectedSyncProviderKey()
            }
        }

        val logout = fragment.findPreference<Preference>(KEY_LOGOUT)!!
        logout.performClick()

        assertNull(usernameAtQueueClearTime)
        assertEquals(SynchronizationProvider.GPODDER_NET.identifier, selectedProviderKeyAtQueueClearTime)

        assertEquals(listOf("clear"), recordingQueue.calls)
        assertTrue(UserPreferences.getGpodnetNotificationsEnabledRaw())
        assertNull(SynchronizationSettings.getSelectedSyncProviderKey())
    }

    @Test
    fun testSyncAndFullSyncRowsCallQueueWithoutStateChange() {
        SynchronizationSettings.setSelectedSyncProvider(SynchronizationProvider.GPODDER_NET.identifier)
        SynchronizationCredentials.setUsername("someone")
        val (_, fragment) = attachFragment()

        val sync = fragment.findPreference<Preference>(KEY_SYNC)!!
        sync.performClick()
        assertEquals(listOf("syncImmediately"), recordingQueue.calls)
        assertEquals(
            SynchronizationProvider.GPODDER_NET.identifier,
            SynchronizationSettings.getSelectedSyncProviderKey()
        )
        assertEquals("someone", SynchronizationCredentials.getUsername())

        recordingQueue.calls.clear()
        val forceFullSync = fragment.findPreference<Preference>(KEY_FORCE_FULL_SYNC)!!
        forceFullSync.performClick()
        assertEquals(listOf("fullSync"), recordingQueue.calls)
        assertEquals(
            SynchronizationProvider.GPODDER_NET.identifier,
            SynchronizationSettings.getSelectedSyncProviderKey()
        )
        assertEquals("someone", SynchronizationCredentials.getUsername())
    }
}
