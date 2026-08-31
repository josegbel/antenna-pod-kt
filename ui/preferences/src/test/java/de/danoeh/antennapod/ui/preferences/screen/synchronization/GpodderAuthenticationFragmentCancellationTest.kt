package de.danoeh.antennapod.ui.preferences.screen.synchronization

import android.content.Context
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import de.danoeh.antennapod.net.sync.gpoddernet.model.GpodnetDevice
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue
import de.danoeh.antennapod.storage.preferences.SynchronizationCredentials
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.preferences.R
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.schedulers.TestScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class GpodderAuthenticationFragmentCancellationTest {

    private lateinit var context: Context
    private val ioScheduler = TestScheduler()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        SynchronizationSettings.init(context)
        SynchronizationCredentials.init(context)
        UserPreferences.init(context)
        SynchronizationQueue.instance = RecordingSynchronizationQueue()
        SynchronizationCredentials.setHosturl("https://gpodder.net")

        RxJavaPlugins.setIoSchedulerHandler { ioScheduler }
        RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }
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

    @Test
    fun testInFlightLoginSurvivesDialogDismissal() {
        val activity = Robolectric.buildActivity(SyncSettingsTestHost::class.java).setup().get()
        val fragment = GpodderAuthenticationFragment()
        fragment.showNow(activity.supportFragmentManager, GpodderAuthenticationFragment.TAG)
        method("advance").invoke(fragment)
        val service = FakeGpodnetService(listOf(GpodnetDevice("dev1", "Device One", "mobile", 0)))
        field("service").set(fragment, service)

        val dialog = fragment.dialog!!
        dialog.findViewById<EditText>(R.id.etxtUsername).setText("someone")
        dialog.findViewById<EditText>(R.id.etxtPassword).setText("secret")
        dialog.findViewById<Button>(R.id.butLogin).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(emptyList<String>(), service.calls)

        fragment.dismiss()
        shadowOf(Looper.getMainLooper()).idle()
        ioScheduler.triggerActions()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf("setCredentials", "login", "getDevices"), service.calls)
        assertNotNull(field("devices").get(fragment))
    }
}
