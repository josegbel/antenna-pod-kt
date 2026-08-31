package de.danoeh.antennapod.ui.preferences.screen.synchronization

import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import de.danoeh.antennapod.net.sync.gpoddernet.model.GpodnetDevice
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue
import de.danoeh.antennapod.storage.preferences.SynchronizationCredentials
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.preferences.R
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class GpodderAuthenticationFragmentCancellationTest {

    private class ManualDispatcher : CoroutineDispatcher() {
        private val queue = ArrayDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queue.addLast(block)
        }

        fun queued(): Int = queue.size

        fun runQueued() {
            while (queue.isNotEmpty()) {
                queue.removeFirst().run()
            }
        }
    }

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        SynchronizationSettings.init(context)
        SynchronizationCredentials.init(context)
        UserPreferences.init(context)
        SynchronizationQueue.instance = RecordingSynchronizationQueue()
        SynchronizationCredentials.setHosturl("https://gpodder.net")
    }

    @After
    fun tearDown() {
        SynchronizationQueue.instance = null
    }

    private fun field(name: String) =
        GpodderAuthenticationFragment::class.java.getDeclaredField(name).apply { isAccessible = true }

    private fun method(name: String, vararg paramTypes: Class<*>) =
        GpodderAuthenticationFragment::class.java.getDeclaredMethod(name, *paramTypes).apply { isAccessible = true }

    @Test
    fun testInFlightLoginIsCancelledWhenDialogIsDismissed() {
        val activity = Robolectric.buildActivity(SyncSettingsTestHost::class.java).setup().get()
        val fragment = GpodderAuthenticationFragment()
        fragment.showNow(activity.supportFragmentManager, GpodderAuthenticationFragment.TAG)
        method("advance").invoke(fragment)
        val service = FakeGpodnetService(listOf(GpodnetDevice("dev1", "Device One", "mobile", 0)))
        field("service").set(fragment, service)
        val manual = ManualDispatcher()
        field("ioDispatcher").set(fragment, manual)

        val dialog = fragment.dialog!!
        dialog.findViewById<EditText>(R.id.etxtUsername).setText("someone")
        dialog.findViewById<EditText>(R.id.etxtPassword).setText("secret")
        dialog.findViewById<Button>(R.id.butLogin).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, manual.queued())
        assertEquals(emptyList<String>(), service.calls)

        fragment.dismiss()
        shadowOf(Looper.getMainLooper()).idle()
        manual.runQueued()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(emptyList<String>(), service.calls)
        assertNull(field("devices").get(fragment))
        assertEquals(View.GONE, dialog.findViewById<TextView>(R.id.credentialsError).visibility)
    }
}
