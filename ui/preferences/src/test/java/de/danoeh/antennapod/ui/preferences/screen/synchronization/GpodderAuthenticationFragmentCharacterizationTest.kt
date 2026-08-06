package de.danoeh.antennapod.ui.preferences.screen.synchronization

import android.app.Dialog
import android.content.Context
import android.os.Build
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.ViewFlipper
import de.danoeh.antennapod.net.sync.gpoddernet.model.GpodnetDevice
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationProvider
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue
import de.danoeh.antennapod.storage.preferences.SynchronizationCredentials
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.preferences.R
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * Pins gaps 13-15 and 17-20. currentStep/service/username/password/selectedDevice/devices are
 * private (D7), so this file drives them via reflection rather than the host-step's UI, which is
 * itself blocked under Robolectric by AntennapodHttpClient's Cache incompatibility (Step 1's D5
 * finding, re-used directly by testHostStepClearsCredentialsAndQueueBeforeSettingHostUrl below).
 */
@RunWith(RobolectricTestRunner::class)
class GpodderAuthenticationFragmentCharacterizationTest {

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

    private fun showFragment(): GpodderAuthenticationFragment {
        val activity = Robolectric.buildActivity(SyncSettingsTestHost::class.java).setup().get()
        val fragment = GpodderAuthenticationFragment()
        fragment.showNow(activity.supportFragmentManager, GpodderAuthenticationFragment.TAG)
        return fragment
    }

    private fun field(name: String) =
        GpodderAuthenticationFragment::class.java.getDeclaredField(name).apply { isAccessible = true }

    private fun method(name: String, vararg paramTypes: Class<*>) =
        GpodderAuthenticationFragment::class.java.getDeclaredMethod(name, *paramTypes).apply { isAccessible = true }

    private fun getCurrentStep(fragment: GpodderAuthenticationFragment): Int = field("currentStep").getInt(fragment)

    private fun setField(fragment: GpodderAuthenticationFragment, name: String, value: Any?) =
        field(name).set(fragment, value)

    private fun invokeAdvance(fragment: GpodderAuthenticationFragment) = method("advance").invoke(fragment)

    private fun invokeGenerateDeviceName(fragment: GpodderAuthenticationFragment): String =
        method("generateDeviceName").invoke(fragment) as String

    private fun invokeGenerateDeviceId(fragment: GpodderAuthenticationFragment, name: String): String =
        method("generateDeviceId", String::class.java).invoke(fragment, name) as String

    @Test
    fun testFirstStepIsSetUpWithoutFlipping() {
        val fragment = showFragment()
        assertEquals(0, getCurrentStep(fragment))
        val viewFlipper = fragment.dialog!!.findViewById<ViewFlipper>(R.id.viewflipper)
        assertEquals(0, viewFlipper.displayedChild)
    }

    @Test
    fun testHostStepClearsCredentialsAndQueueBeforeSettingHostUrl() {
        SynchronizationCredentials.setUsername("oldUser")
        SynchronizationCredentials.setPassword("oldPass")
        SynchronizationCredentials.setDeviceId("oldDevice")

        val fragment = showFragment()
        val dialog = fragment.dialog!!
        val serverUrlText = dialog.findViewById<EditText>(R.id.serverUrlText)
        val chooseHostButton = dialog.findViewById<Button>(R.id.chooseHostButton)
        serverUrlText.setText("https://custom.host")

        // AntennapodHttpClient.getHttpClient() is not constructible under Robolectric (Step 1's
        // D5 fallback finding, testAntennapodHttpClientConstructionUnderRobolectric) -- the click
        // handler throws while constructing GpodnetService, *after* the credential-clearing and
        // host-url-setting statements have already run. The ordering this pins survives that
        // construction failure exactly as D5 anticipated.
        assertThrows(NullPointerException::class.java) {
            chooseHostButton.performClick()
        }

        assertNull(SynchronizationCredentials.getUsername())
        assertNull(SynchronizationCredentials.getPassword())
        assertNull(SynchronizationCredentials.getDeviceId())
        assertEquals("https://custom.host", SynchronizationCredentials.getHosturl())
        assertEquals(listOf("clear"), recordingQueue.calls)
        assertEquals(0, getCurrentStep(fragment))
    }

    @Test
    fun testUsernameValidationRunsBeforeAnyNetworkCall() {
        // setupLoginView() dereferences SynchronizationCredentials.getHosturl() unguarded
        // (kotlin finding 4) -- safe in production only because setupHostView() always sets it
        // first. Set it here too, matching that real precondition.
        SynchronizationCredentials.setHosturl("https://gpodder.net")
        val fragment = showFragment()
        invokeAdvance(fragment) // STEP_HOSTNAME -> STEP_LOGIN, wires up the credentials view
        val dialog = fragment.dialog!!
        val usernameField = dialog.findViewById<EditText>(R.id.etxtUsername)
        val passwordField = dialog.findViewById<EditText>(R.id.etxtPassword)
        val loginButton = dialog.findViewById<Button>(R.id.butLogin)
        val errorText = dialog.findViewById<TextView>(R.id.credentialsError)
        val progressBar = dialog.findViewById<ProgressBar>(R.id.progBarLogin)

        usernameField.setText("bad!user")
        passwordField.setText("whatever")
        loginButton.performClick()

        assertEquals(context.getString(R.string.gpodnetsync_username_characters_error), errorText.text.toString())
        assertEquals(View.VISIBLE, errorText.visibility)
        // Validation short-circuited before the login.setEnabled(false)/progressBar-visible pair
        // that only runs once a network call is actually kicked off -- proving no RxJava
        // subscription (and hence no network call) was ever created.
        assertTrue(loginButton.isEnabled)
        assertEquals(View.GONE, progressBar.visibility)
    }

    @Test
    fun testDeviceNameGenerationDedupesByIdAndByCaption() {
        val fragment = showFragment()
        val baseName = context.getString(R.string.gpodnetauth_device_name_default, Build.MODEL)
        setField(
            fragment,
            "devices",
            mutableListOf(
                GpodnetDevice(invokeGenerateDeviceId(fragment, baseName), baseName, "mobile", 0),
                GpodnetDevice("some-other-id", "$baseName (1)", "mobile", 0)
            )
        )
        val generated = invokeGenerateDeviceName(fragment)
        // baseName is taken (matched by id), "$baseName (1)" is taken (matched by caption, not
        // id) -- proving isDeviceInList dedupes on *either* signal, not just one.
        assertEquals("$baseName (2)", generated)
    }

    @Test
    fun testDeviceIdUsesLocaleUsLowercase() {
        val fragment = showFragment()
        val previousDefault = Locale.getDefault()
        try {
            // Turkish locale lower-cases "I" to dotless "ı", not "i" -- the classic JVM
            // locale hazard. generateDeviceId must use Locale.US explicitly, ignoring this.
            Locale.setDefault(Locale.forLanguageTag("tr"))
            val id = invokeGenerateDeviceId(fragment, "MY DEVICE I")
            assertEquals("my_device_i", id)
        } finally {
            Locale.setDefault(previousDefault)
        }
    }

    @Test
    fun testCredentialCommitOrderOnDeviceToFinishTransition() {
        SynchronizationCredentials.setHosturl("https://gpodder.net")
        val fragment = showFragment()
        invokeAdvance(fragment) // -> STEP_LOGIN
        setField(fragment, "username", "newUser")
        setField(fragment, "password", "newPass")
        setField(fragment, "devices", mutableListOf<GpodnetDevice>())
        invokeAdvance(fragment) // -> STEP_DEVICE
        setField(fragment, "selectedDevice", GpodnetDevice("dev1", "Device One", "mobile", 0))
        invokeAdvance(fragment) // -> STEP_FINISH, commits credentials

        assertEquals(
            SynchronizationProvider.GPODDER_NET.identifier,
            SynchronizationSettings.getSelectedSyncProviderKey()
        )
        assertEquals("newUser", SynchronizationCredentials.getUsername())
        assertEquals("newPass", SynchronizationCredentials.getPassword())
        assertEquals("dev1", SynchronizationCredentials.getDeviceId())
        assertEquals(3, getCurrentStep(fragment))
    }

    @Test
    fun testPartialWizardLeavesCredentialsUntouched() {
        SynchronizationCredentials.setHosturl("https://gpodder.net")
        val fragment = showFragment()
        invokeAdvance(fragment) // -> STEP_LOGIN
        setField(fragment, "username", "newUser")
        setField(fragment, "password", "newPass")
        setField(fragment, "devices", mutableListOf<GpodnetDevice>())
        invokeAdvance(fragment) // -> STEP_DEVICE, no commit yet -- wizard abandoned here

        assertNull(SynchronizationCredentials.getUsername())
        assertNull(SynchronizationCredentials.getPassword())
        assertNull(SynchronizationCredentials.getDeviceId())
        assertNull(SynchronizationSettings.getSelectedSyncProviderKey())
    }

    @Test
    fun testFinishStepDismissesBeforeSyncing() {
        SynchronizationCredentials.setHosturl("https://gpodder.net")
        val activity = Robolectric.buildActivity(SyncSettingsTestHost::class.java).setup().get()
        val fragment = TestableGpodderAuthenticationFragment()
        fragment.showNow(activity.supportFragmentManager, GpodderAuthenticationFragment.TAG)

        invokeAdvance(fragment) // -> STEP_LOGIN
        setField(fragment, "username", "newUser")
        setField(fragment, "password", "newPass")
        setField(fragment, "devices", mutableListOf<GpodnetDevice>())
        invokeAdvance(fragment) // -> STEP_DEVICE
        setField(fragment, "selectedDevice", GpodnetDevice("dev1", "Device One", "mobile", 0))
        invokeAdvance(fragment) // -> STEP_FINISH

        recordingQueue.onCall = { name -> if (name == "syncImmediately") fragment.eventLog.add("syncImmediately") }
        val dialog = fragment.dialog!!
        val syncButton = dialog.findViewById<Button>(R.id.butSyncNow)
        syncButton.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf("dismiss", "syncImmediately"), fragment.eventLog)
    }

    @Test
    fun testDialogIsNonCancellableInBothSenses() {
        val fragment = showFragment()
        assertFalse(fragment.isCancelable)
        val dialog = fragment.dialog!!
        val cancelableField = Dialog::class.java.getDeclaredField("mCancelable").apply { isAccessible = true }
        assertFalse(cancelableField.getBoolean(dialog))
    }

    class TestableGpodderAuthenticationFragment : GpodderAuthenticationFragment() {
        val eventLog = mutableListOf<String>()

        override fun dismiss() {
            eventLog.add("dismiss")
            super.dismiss()
        }
    }
}
