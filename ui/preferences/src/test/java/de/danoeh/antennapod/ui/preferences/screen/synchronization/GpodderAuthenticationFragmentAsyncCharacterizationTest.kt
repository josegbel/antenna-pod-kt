package de.danoeh.antennapod.ui.preferences.screen.synchronization

import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import de.danoeh.antennapod.net.sync.gpoddernet.GpodnetServiceAuthenticationException
import de.danoeh.antennapod.net.sync.gpoddernet.GpodnetServiceException
import de.danoeh.antennapod.net.sync.gpoddernet.model.GpodnetDevice
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue
import de.danoeh.antennapod.storage.preferences.SynchronizationCredentials
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.preferences.R
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.exceptions.CompositeException
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.IOException
import org.junit.After
import org.junit.Assert.assertEquals
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
class GpodderAuthenticationFragmentAsyncCharacterizationTest {

    private lateinit var context: Context

    private var capturedCulprit: Throwable? = null

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        SynchronizationSettings.init(context)
        SynchronizationCredentials.init(context)
        UserPreferences.init(context)
        SynchronizationQueue.instance = RecordingSynchronizationQueue()
        SynchronizationCredentials.setHosturl("https://gpodder.net")

        capturedCulprit = null
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }
        RxJavaPlugins.setErrorHandler { error ->
            capturedCulprit = if (error is CompositeException) {
                error.exceptions.firstOrNull { it is NullPointerException } ?: error
            } else {
                error
            }
        }
    }

    @After
    fun tearDown() {
        SynchronizationQueue.instance = null
        RxJavaPlugins.reset()
        RxAndroidPlugins.reset()
    }

    private fun field(name: String) =
        GpodderAuthenticationFragment::class.java.getDeclaredField(name).apply { isAccessible = true }

    private fun method(name: String, vararg paramTypes: Class<*>) =
        GpodderAuthenticationFragment::class.java.getDeclaredMethod(name, *paramTypes).apply { isAccessible = true }

    private fun currentStep(fragment: GpodderAuthenticationFragment): Int = field("currentStep").getInt(fragment)

    private fun showLoginStep(service: FakeGpodnetService): GpodderAuthenticationFragment {
        val activity = Robolectric.buildActivity(SyncSettingsTestHost::class.java).setup().get()
        val fragment = GpodderAuthenticationFragment()
        fragment.showNow(activity.supportFragmentManager, GpodderAuthenticationFragment.TAG)
        method("advance").invoke(fragment)
        field("service").set(fragment, service)
        return fragment
    }

    private fun clickLogin(fragment: GpodderAuthenticationFragment, username: String, password: String) {
        val dialog = fragment.dialog!!
        dialog.findViewById<EditText>(R.id.etxtUsername).setText(username)
        dialog.findViewById<EditText>(R.id.etxtPassword).setText(password)
        dialog.findViewById<Button>(R.id.butLogin).performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun showDeviceStep(service: FakeGpodnetService): GpodderAuthenticationFragment {
        val fragment = showLoginStep(service)
        clickLogin(fragment, "someone", "secret")
        return fragment
    }

    @Test
    fun testLoginSuccessCallsServiceInOrderAndAdvancesToDeviceStep() {
        val service = FakeGpodnetService(listOf(GpodnetDevice("dev1", "Device One", "mobile", 0)))
        val fragment = showLoginStep(service)

        clickLogin(fragment, "someone", "secret")

        assertEquals(listOf("setCredentials", "login", "getDevices"), service.calls)
        @Suppress("UNCHECKED_CAST")
        val devices = field("devices").get(fragment) as List<GpodnetDevice>
        assertEquals(1, devices.size)
        assertEquals("dev1", devices[0].id)
        assertEquals("someone", field("username").get(fragment))
        assertEquals("secret", field("password").get(fragment))
        val dialog = fragment.dialog!!
        assertTrue(dialog.findViewById<Button>(R.id.butLogin).isEnabled)
        assertEquals(View.GONE, dialog.findViewById<ProgressBar>(R.id.progBarLogin).visibility)
        assertEquals(2, currentStep(fragment))
    }

    @Test
    fun testLoginWritesDevicesBeforeCredentialFields() {
        val service = FakeGpodnetService()
        val fragment = showLoginStep(service)
        var usernameAtGetDevices: Any? = "unset"
        service.beforeGetDevices = { usernameAtGetDevices = field("username").get(fragment) }

        clickLogin(fragment, "someone", "secret")

        assertNull(usernameAtGetDevices)
        assertEquals("someone", field("username").get(fragment))
    }

    @Test
    fun testLoginErrorWithCauseRendersCauseMessage() {
        val service = FakeGpodnetService()
        service.loginError = GpodnetServiceException(IOException("boom"))
        val fragment = showLoginStep(service)

        clickLogin(fragment, "someone", "secret")

        val dialog = fragment.dialog!!
        assertEquals("boom", dialog.findViewById<TextView>(R.id.credentialsError).text.toString())
        assertEquals(View.VISIBLE, dialog.findViewById<TextView>(R.id.credentialsError).visibility)
        assertTrue(dialog.findViewById<Button>(R.id.butLogin).isEnabled)
        assertEquals(View.GONE, dialog.findViewById<ProgressBar>(R.id.progBarLogin).visibility)
        assertEquals(1, currentStep(fragment))
    }

    @Test
    fun testWrongPasswordErrorPathThrowsFromNullCause() {
        val service = FakeGpodnetService()
        service.loginError = GpodnetServiceAuthenticationException("Wrong username or password")
        val fragment = showLoginStep(service)

        clickLogin(fragment, "someone", "secret")

        assertTrue(capturedCulprit is NullPointerException)
    }

    @Test
    fun testCreateDeviceSuccessConfiguresLowercasedIdAndAdvances() {
        val service = FakeGpodnetService()
        val fragment = showDeviceStep(service)
        val dialog = fragment.dialog!!
        dialog.findViewById<EditText>(R.id.deviceName).setText("My Device")

        dialog.findViewById<Button>(R.id.createDeviceButton).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf("my_device"), service.configuredDeviceIds)
        assertEquals(listOf<String?>("My Device"), service.configuredCaptions)
        assertEquals("my_device", (field("selectedDevice").get(fragment) as GpodnetDevice).id)
        assertEquals(View.GONE, dialog.findViewById<ProgressBar>(R.id.progbarCreateDevice).visibility)
        assertEquals(3, currentStep(fragment))
    }

    @Test
    fun testCreateDeviceErrorRendersErrorMessageNotCause() {
        val service = FakeGpodnetService()
        service.configureDeviceError = GpodnetServiceException("nope")
        val fragment = showDeviceStep(service)
        val dialog = fragment.dialog!!
        dialog.findViewById<EditText>(R.id.deviceName).setText("My Device")

        dialog.findViewById<Button>(R.id.createDeviceButton).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("nope", dialog.findViewById<TextView>(R.id.deviceSelectError).text.toString())
        assertTrue(dialog.findViewById<EditText>(R.id.deviceName).isEnabled)
    }
}
