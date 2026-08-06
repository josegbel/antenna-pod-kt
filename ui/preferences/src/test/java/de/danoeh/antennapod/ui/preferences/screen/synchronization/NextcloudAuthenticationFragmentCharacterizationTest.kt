package de.danoeh.antennapod.ui.preferences.screen.synchronization

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import de.danoeh.antennapod.net.sync.nextcloud.NextcloudLoginFlow
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationProvider
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue
import de.danoeh.antennapod.storage.preferences.SynchronizationCredentials
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.preferences.R
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
class NextcloudAuthenticationFragmentCharacterizationTest {

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

    private fun showFragment(): Pair<AppCompatActivity, NextcloudAuthenticationFragment> {
        val activity = Robolectric.buildActivity(SyncSettingsTestHost::class.java).setup().get()
        val fragment = NextcloudAuthenticationFragment()
        fragment.showNow(activity.supportFragmentManager, NextcloudAuthenticationFragment.TAG)
        return activity to fragment
    }

    private fun loginFlowField() =
        NextcloudAuthenticationFragment::class.java.getDeclaredField("nextcloudLoginFlow").apply { isAccessible = true }

    private fun extraLoginFlowKey(): String =
        NextcloudAuthenticationFragment::class.java.getDeclaredField("EXTRA_LOGIN_FLOW")
            .apply { isAccessible = true }.get(null) as String

    @Test
    fun testCredentialCommitOrderDiffersFromGpodderPath() {
        val (_, fragment) = showFragment()

        var selectedProviderKeyAtClearTime: String? = "unset"
        var usernameAtClearTime: String? = "unset"
        var passwordAtClearTime: String? = "unset"
        recordingQueue.onCall = { name ->
            if (name == "clear") {
                selectedProviderKeyAtClearTime = SynchronizationSettings.getSelectedSyncProviderKey()
                usernameAtClearTime = SynchronizationCredentials.getUsername()
                passwordAtClearTime = SynchronizationCredentials.getPassword()
            }
        }

        fragment.onNextcloudAuthenticated("https://nc.example.com", "someUser", "somePass")

        // Unlike the gpodder path (where queue.clear() runs in the host step, long before any
        // provider is selected), here setSelectedSyncProvider() has already run by the time
        // queue.clear() fires -- proving this path's different interleaving.
        assertEquals(SynchronizationProvider.NEXTCLOUD_GPODDER.identifier, selectedProviderKeyAtClearTime)
        // credentials.clear() has already run by queue.clear() time, same as the gpodder path.
        assertNull(usernameAtClearTime)
        assertNull(passwordAtClearTime)

        assertEquals(listOf("clear", "fullSync"), recordingQueue.calls)
        assertEquals("someUser", SynchronizationCredentials.getUsername())
        assertEquals("somePass", SynchronizationCredentials.getPassword())
        assertEquals("https://nc.example.com", SynchronizationCredentials.getHosturl())
        assertEquals(
            SynchronizationProvider.NEXTCLOUD_GPODDER.identifier,
            SynchronizationSettings.getSelectedSyncProviderKey()
        )
    }

    @Test
    fun testDeferredDismissWhenNotResumed() {
        val controller = Robolectric.buildActivity(SyncSettingsTestHost::class.java)
        val activity = controller.setup().get()
        val fragment = NextcloudAuthenticationFragment()
        fragment.showNow(activity.supportFragmentManager, NextcloudAuthenticationFragment.TAG)

        controller.pause()
        assertFalse(fragment.isResumed)

        fragment.onNextcloudAuthenticated("https://nc.example.com", "someUser", "somePass")

        // Not resumed: dismiss() must be deferred, not called immediately -- the dialog is
        // still showing.
        assertNotNull(fragment.dialog)
        assertTrue(fragment.dialog!!.isShowing)

        controller.resume()
        // onResume()'s shouldDismiss check now dismisses it.
        assertFalse(fragment.dialog?.isShowing ?: false)
    }

    @Test
    fun testLoginFlowSurvivesConfigurationChange() {
        val (_, fragment) = showFragment()
        val fakeFlow = RecordingNextcloudLoginFlow(
            OkHttpClient(),
            "https://nc.example.com",
            context,
            fragment
        )
        loginFlowField().set(fragment, fakeFlow)

        val outState = Bundle()
        fragment.onSaveInstanceState(outState)
        assertEquals(fakeFlow.savedState, outState.getStringArrayList(extraLoginFlowKey()))

        // A fragment with no login flow yet started must not write the key at all.
        val (_, freshFragment) = showFragment()
        val freshOutState = Bundle()
        freshFragment.onSaveInstanceState(freshOutState)
        assertNull(freshOutState.getStringArrayList(extraLoginFlowKey()))

        // Restoring: onCreateDialog's guard (savedInstanceState has EXTRA_LOGIN_FLOW) must be
        // entered when the key is present. onCreateDialog is public, so it can be invoked
        // directly against an attached-but-not-yet-shown fragment instance.
        // AntennapodHttpClient.getHttpClient() is not constructible under Robolectric (Step 1's
        // D5 finding, re-used exactly as gap 14's test uses it) -- restoration crashes there,
        // which is itself proof the guard let execution reach
        // NextcloudLoginFlow.fromInstanceState's call site.
        val savedInstanceState = Bundle()
        savedInstanceState.putStringArrayList(extraLoginFlowKey(), fakeFlow.savedState)
        val activity = Robolectric.buildActivity(SyncSettingsTestHost::class.java).setup().get()
        val restoredFragment = NextcloudAuthenticationFragment()
        activity.supportFragmentManager.beginTransaction().add(restoredFragment, "nextcloud-restore").commitNow()
        assertThrows(NullPointerException::class.java) {
            restoredFragment.onCreateDialog(savedInstanceState)
        }

        // The negative case: no EXTRA_LOGIN_FLOW key means the guard is not entered, and the
        // dialog is created normally with no crash.
        val normalFragment = NextcloudAuthenticationFragment()
        activity.supportFragmentManager.beginTransaction().add(normalFragment, "nextcloud-normal").commitNow()
        val normalDialog = normalFragment.onCreateDialog(Bundle())
        assertNotNull(normalDialog)
    }

    @Test
    fun testDismissCancelsLoginFlow() {
        val (_, fragment) = showFragment()
        val fakeFlow = RecordingNextcloudLoginFlow(OkHttpClient(), "https://nc.example.com", context, fragment)
        loginFlowField().set(fragment, fakeFlow)

        fragment.onDismiss(fragment.dialog as DialogInterface)
        assertTrue(fakeFlow.cancelCalled)
    }

    @Test
    fun testDismissWithNoLoginFlowDoesNotCrash() {
        val (_, fragment) = showFragment()
        assertNull(loginFlowField().get(fragment))
        fragment.onDismiss(fragment.dialog as DialogInterface)
    }

    @Test
    fun testErrorDialogAppliesSpanToSecondHalfOnly() {
        val (_, fragment) = showFragment()
        val before = ShadowDialog.getLatestDialog()

        fragment.onNextcloudAuthError("Some network problem")

        val errorDialog = ShadowDialog.getLatestDialog()
        assertNotNull(errorDialog)
        assertTrue(before == null || before !== errorDialog)

        val messageView = errorDialog.findViewById<TextView>(android.R.id.message)
        assertNotNull(messageView)
        val genericMessage = context.getString(R.string.nextcloud_login_error_generic)
        val expectedFullText = "$genericMessage\n\nSome network problem"
        assertEquals(expectedFullText, messageView.text.toString())

        val spanned = messageView.text as Spanned
        val spans = spanned.getSpans(0, spanned.length, ForegroundColorSpan::class.java)
        assertEquals(1, spans.size)
        val span = spans[0]
        assertEquals(0x88888888.toInt(), span.foregroundColor)
        assertEquals(genericMessage.length, spanned.getSpanStart(span))
        assertEquals(expectedFullText.length, spanned.getSpanEnd(span))
    }

    private open class RecordingNextcloudLoginFlow(
        httpClient: OkHttpClient,
        hostUrl: String,
        context: Context,
        callback: AuthenticationCallback
    ) : NextcloudLoginFlow(httpClient, hostUrl, context, callback) {
        var cancelCalled = false
        val savedState: ArrayList<String> = arrayListOf(hostUrl, "tok123", "https://nc.example.com/endpoint")

        override fun cancel() {
            cancelCalled = true
        }

        override fun saveInstanceState(): ArrayList<String> = savedState
    }
}
