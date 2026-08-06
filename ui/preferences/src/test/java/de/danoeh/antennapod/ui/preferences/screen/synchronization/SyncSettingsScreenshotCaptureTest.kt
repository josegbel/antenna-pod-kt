package de.danoeh.antennapod.ui.preferences.screen.synchronization

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue
import de.danoeh.antennapod.storage.preferences.SynchronizationCredentials
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.common.R as CommonR
import de.danoeh.antennapod.ui.preferences.R
import java.io.File
import java.io.FileOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class SyncSettingsScreenshotCaptureTest {

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

    private data class CaptureResult(
        val bitmap: Bitmap,
        val recyclerView: RecyclerView,
        val toolbar: MaterialToolbar
    )

    private fun capture(): CaptureResult {
        val activity = Robolectric.buildActivity(SyncSettingsCaptureHost::class.java).setup().get()
        val fragment = SynchronizationPreferencesFragment()
        activity.supportFragmentManager.beginTransaction()
            .add(R.id.settingsContainer, fragment, "sync-settings")
            .commitNow()

        val decorView = activity.window.decorView
        val metrics = activity.resources.displayMetrics
        val widthSpec = View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY)
        decorView.measure(widthSpec, heightSpec)
        decorView.layout(0, 0, decorView.measuredWidth, decorView.measuredHeight)

        val bitmap = Bitmap.createBitmap(decorView.width, decorView.height, Bitmap.Config.ARGB_8888)
        decorView.draw(Canvas(bitmap))

        val toolbar = activity.findViewById<MaterialToolbar>(CommonR.id.toolbar)
        return CaptureResult(bitmap, fragment.listView, toolbar)
    }

    private fun View.verticalOffsetInWindow(): Int {
        var offset = 0
        var current: View? = this
        while (current != null) {
            offset += current.top
            current = current.parent as? View
        }
        return offset
    }

    @Test
    fun testCapturedBitmapIsNotBlankAndHasExpectedDimensions() {
        val bitmap = capture().bitmap
        assertEquals(822, bitmap.width)
        assertEquals(1782, bitmap.height)

        val distinctColors = mutableSetOf<Int>()
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                distinctColors.add(bitmap.getPixel(x, y))
            }
        }
        assertTrue(distinctColors.size >= 200)
    }

    @Test
    fun testFirstPreferenceRowIsNotClippedByActionBar() {
        val result = capture()
        assertEquals(4, result.recyclerView.childCount)
        assertTrue(result.toolbar.height > 0)

        val toolbarBottom = result.toolbar.verticalOffsetInWindow() + result.toolbar.height
        val firstRowTop = result.recyclerView.getChildAt(0).verticalOffsetInWindow()
        assertTrue(firstRowTop >= toolbarBottom)
    }

    @Test
    fun testWritesPngUnderModuleBuildDirectory() {
        val bitmap = capture().bitmap
        val outputDir = File("build/reports/screenshots")
        outputDir.mkdirs()
        val outputFile = File(outputDir, "sync_settings_before_milestone_15b.png")
        FileOutputStream(outputFile).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        println("Screenshot written to: ${outputFile.absoluteFile}")
        assertTrue(outputFile.exists())
        assertTrue(outputFile.length() > 10_000)
    }
}
