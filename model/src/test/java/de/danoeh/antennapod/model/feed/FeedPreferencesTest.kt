package de.danoeh.antennapod.model.feed

import android.text.TextUtils
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.Arrays
import java.util.HashSet
import java.util.regex.Pattern
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic

// android.text.TextUtils is an unmocked Android SDK stub in :model's bare-JVM unit tests
// (no Robolectric, per module policy). FeedPreferences.getTagsAsString() calls TextUtils.join(),
// which throws "not mocked" if invoked for real here. A MockedStatic gives it its real AOSP
// semantics so this test characterizes FeedPreferences' own logic against genuine device
// behavior rather than the stub's throw.
class FeedPreferencesTest {

    private lateinit var textUtilsMock: MockedStatic<TextUtils>

    @Before
    fun setUp() {
        textUtilsMock = mockStatic(TextUtils::class.java)
        textUtilsMock.`when`<String> { TextUtils.join(any(), any<Iterable<*>>()) }.thenAnswer { invocation ->
            val delimiter: CharSequence = invocation.getArgument(0)
            val tokens: Iterable<*> = invocation.getArgument(1)
            val sb = StringBuilder()
            var firstTime = true
            for (token in tokens) {
                if (firstTime) {
                    firstTime = false
                } else {
                    sb.append(delimiter)
                }
                sb.append(token)
            }
            sb.toString()
        }
    }

    @After
    fun tearDown() {
        textUtilsMock.close()
    }

    private fun newPreferences(
        feedPlaybackSpeed: Float,
        feedSkipSilence: FeedPreferences.SkipSilence,
        tags: Set<String>
    ): FeedPreferences {
        return FeedPreferences(
            1, FeedPreferences.AutoDownloadSetting.GLOBAL, true,
            FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF, "user", "pass",
            FeedFilter(), feedPlaybackSpeed, 0, 0, feedSkipSilence, false,
            FeedPreferences.NewEpisodesAction.GLOBAL, tags
        )
    }

    @Test
    fun autoDeleteActionCodesAndFromCode() {
        assertEquals(0, FeedPreferences.AutoDeleteAction.GLOBAL.code)
        assertEquals(1, FeedPreferences.AutoDeleteAction.ALWAYS.code)
        assertEquals(2, FeedPreferences.AutoDeleteAction.NEVER.code)

        assertEquals(FeedPreferences.AutoDeleteAction.GLOBAL, FeedPreferences.AutoDeleteAction.fromCode(0))
        assertEquals(FeedPreferences.AutoDeleteAction.ALWAYS, FeedPreferences.AutoDeleteAction.fromCode(1))
        assertEquals(FeedPreferences.AutoDeleteAction.NEVER, FeedPreferences.AutoDeleteAction.fromCode(2))
        assertEquals(FeedPreferences.AutoDeleteAction.NEVER, FeedPreferences.AutoDeleteAction.fromCode(99))
    }

    @Test
    fun newEpisodesActionCodesAndFromCode() {
        assertEquals(0, FeedPreferences.NewEpisodesAction.GLOBAL.code)
        assertEquals(1, FeedPreferences.NewEpisodesAction.ADD_TO_INBOX.code)
        assertEquals(3, FeedPreferences.NewEpisodesAction.ADD_TO_QUEUE.code)
        assertEquals(2, FeedPreferences.NewEpisodesAction.NOTHING.code)

        assertEquals(FeedPreferences.NewEpisodesAction.GLOBAL, FeedPreferences.NewEpisodesAction.fromCode(0))
        assertEquals(FeedPreferences.NewEpisodesAction.ADD_TO_INBOX, FeedPreferences.NewEpisodesAction.fromCode(1))
        assertEquals(FeedPreferences.NewEpisodesAction.NOTHING, FeedPreferences.NewEpisodesAction.fromCode(2))
        assertEquals(FeedPreferences.NewEpisodesAction.ADD_TO_QUEUE, FeedPreferences.NewEpisodesAction.fromCode(3))
        assertEquals(FeedPreferences.NewEpisodesAction.ADD_TO_INBOX, FeedPreferences.NewEpisodesAction.fromCode(99))
    }

    @Test
    fun skipSilenceCodesAndFromCode() {
        assertEquals(0, FeedPreferences.SkipSilence.OFF.code)
        assertEquals(1, FeedPreferences.SkipSilence.GLOBAL.code)
        assertEquals(2, FeedPreferences.SkipSilence.AGGRESSIVE.code)

        assertEquals(FeedPreferences.SkipSilence.OFF, FeedPreferences.SkipSilence.fromCode(0))
        assertEquals(FeedPreferences.SkipSilence.GLOBAL, FeedPreferences.SkipSilence.fromCode(1))
        assertEquals(FeedPreferences.SkipSilence.AGGRESSIVE, FeedPreferences.SkipSilence.fromCode(2))
        assertEquals(FeedPreferences.SkipSilence.GLOBAL, FeedPreferences.SkipSilence.fromCode(99))
    }

    @Test
    fun autoDownloadSettingCodesAndFactories() {
        assertEquals(0, FeedPreferences.AutoDownloadSetting.DISABLED.code)
        assertEquals(2, FeedPreferences.AutoDownloadSetting.ENABLED.code)
        assertEquals(1, FeedPreferences.AutoDownloadSetting.GLOBAL.code)

        assertEquals(FeedPreferences.AutoDownloadSetting.DISABLED, FeedPreferences.AutoDownloadSetting.fromInteger(0))
        assertEquals(FeedPreferences.AutoDownloadSetting.ENABLED, FeedPreferences.AutoDownloadSetting.fromInteger(2))
        assertEquals(FeedPreferences.AutoDownloadSetting.GLOBAL, FeedPreferences.AutoDownloadSetting.fromInteger(1))
        assertEquals(FeedPreferences.AutoDownloadSetting.GLOBAL, FeedPreferences.AutoDownloadSetting.fromInteger(99))

        assertEquals(
            FeedPreferences.AutoDownloadSetting.ENABLED,
            FeedPreferences.AutoDownloadSetting.fromBoolean(true)
        )
        assertEquals(
            FeedPreferences.AutoDownloadSetting.DISABLED,
            FeedPreferences.AutoDownloadSetting.fromBoolean(false)
        )
    }

    @Test
    fun getFeedSkipSilenceReturnsGlobalWhenSpeedIsUseGlobal() {
        val prefs = newPreferences(
            FeedPreferences.SPEED_USE_GLOBAL,
            FeedPreferences.SkipSilence.OFF,
            HashSet()
        )
        assertEquals(FeedPreferences.SkipSilence.GLOBAL, prefs.getFeedSkipSilence())
    }

    @Test
    fun getFeedSkipSilenceReturnsStoredWhenSpeedSet() {
        val prefs = newPreferences(1.5f, FeedPreferences.SkipSilence.OFF, HashSet())
        assertEquals(FeedPreferences.SkipSilence.OFF, prefs.getFeedSkipSilence())
    }

    @Test
    fun isAutoDownloadEnabledTrue() {
        val prefs = FeedPreferences(
            1,
            FeedPreferences.AutoDownloadSetting.ENABLED,
            FeedPreferences.AutoDeleteAction.GLOBAL,
            VolumeAdaptionSetting.OFF,
            FeedPreferences.NewEpisodesAction.GLOBAL,
            "user",
            "pass"
        )
        assertTrue(prefs.isAutoDownload(false))
        assertTrue(prefs.isAutoDownload(true))
    }

    @Test
    fun isAutoDownloadDisabledFalse() {
        val prefs = FeedPreferences(
            1,
            FeedPreferences.AutoDownloadSetting.DISABLED,
            FeedPreferences.AutoDeleteAction.GLOBAL,
            VolumeAdaptionSetting.OFF,
            FeedPreferences.NewEpisodesAction.GLOBAL,
            "user",
            "pass"
        )
        assertFalse(prefs.isAutoDownload(false))
        assertFalse(prefs.isAutoDownload(true))
    }

    @Test
    fun isAutoDownloadGlobalUsesDefault() {
        val prefs = FeedPreferences(
            1,
            FeedPreferences.AutoDownloadSetting.GLOBAL,
            FeedPreferences.AutoDeleteAction.GLOBAL,
            VolumeAdaptionSetting.OFF,
            FeedPreferences.NewEpisodesAction.GLOBAL,
            "user",
            "pass"
        )
        assertTrue(prefs.isAutoDownload(true))
        assertFalse(prefs.isAutoDownload(false))
    }

    @Test
    fun updateFromOtherCopiesOnlyUsernamePassword() {
        val prefs = FeedPreferences(
            1,
            FeedPreferences.AutoDownloadSetting.DISABLED,
            FeedPreferences.AutoDeleteAction.ALWAYS,
            VolumeAdaptionSetting.OFF,
            FeedPreferences.NewEpisodesAction.GLOBAL,
            "oldUser",
            "oldPass"
        )
        val other = FeedPreferences(
            2,
            FeedPreferences.AutoDownloadSetting.ENABLED,
            FeedPreferences.AutoDeleteAction.NEVER,
            VolumeAdaptionSetting.OFF,
            FeedPreferences.NewEpisodesAction.ADD_TO_QUEUE,
            "newUser",
            "newPass"
        )

        prefs.updateFromOther(other)

        assertEquals("newUser", prefs.getUsername())
        assertEquals("newPass", prefs.getPassword())
        assertEquals(1L, prefs.getFeedID())
        assertEquals(FeedPreferences.AutoDownloadSetting.DISABLED, prefs.getAutoDownload())
        assertEquals(FeedPreferences.AutoDeleteAction.ALWAYS, prefs.getAutoDeleteAction())
    }

    @Test
    fun updateFromOtherNullIsNoOp() {
        val prefs = FeedPreferences(
            1,
            FeedPreferences.AutoDownloadSetting.DISABLED,
            FeedPreferences.AutoDeleteAction.ALWAYS,
            VolumeAdaptionSetting.OFF,
            FeedPreferences.NewEpisodesAction.GLOBAL,
            "user",
            "pass"
        )

        prefs.updateFromOther(null)

        assertEquals("user", prefs.getUsername())
        assertEquals("pass", prefs.getPassword())
    }

    @Test
    fun getTagsAsStringEmptyReturnsEmpty() {
        val prefs = newPreferences(
            FeedPreferences.SPEED_USE_GLOBAL,
            FeedPreferences.SkipSilence.GLOBAL,
            HashSet()
        )
        assertEquals("", prefs.getTagsAsString())
    }

    @Test
    fun getTagsAsStringSingleTagNoSeparator() {
        val tags: MutableSet<String> = HashSet()
        tags.add("news")
        val prefs = newPreferences(
            FeedPreferences.SPEED_USE_GLOBAL,
            FeedPreferences.SkipSilence.GLOBAL,
            tags
        )
        assertEquals("news", prefs.getTagsAsString())
    }

    @Test
    fun getTagsAsStringJoinsWithSeparator() {
        val tags: MutableSet<String> = HashSet()
        tags.add("news")
        tags.add("tech")
        val prefs = newPreferences(
            FeedPreferences.SPEED_USE_GLOBAL,
            FeedPreferences.SkipSilence.GLOBAL,
            tags
        )

        val result = prefs.getTagsAsString()
        // Pattern.compile(regex).split(input) (implicit limit 0) is byte-for-byte identical to Java's
        // String.split(String) — do not simplify to Kotlin's .split(Regex)/CharSequence.split(vararg
        // String), which do not strip trailing empty tokens the same way (see FeedFunding.kt's
        // identical "do not simplify" regression-guard comment for the same underlying risk).
        val parts: Set<String> = HashSet(Arrays.asList(*Pattern.compile(FeedPreferences.TAG_SEPARATOR).split(result)))
        assertEquals(tags, parts)
    }

    @Test
    fun sevenArgConstructorAppliesDefaults() {
        val prefs = FeedPreferences(
            1,
            FeedPreferences.AutoDownloadSetting.GLOBAL,
            FeedPreferences.AutoDeleteAction.GLOBAL,
            VolumeAdaptionSetting.OFF,
            FeedPreferences.NewEpisodesAction.GLOBAL,
            "user",
            "pass"
        )

        assertTrue(prefs.getKeepUpdated())
        assertNotNull(prefs.getFilter())
        assertEquals(FeedPreferences.SPEED_USE_GLOBAL, prefs.getFeedPlaybackSpeed(), 0.0001f)
    }

    @Test
    fun serializationRoundTripPreservesFields() {
        val tags: MutableSet<String> = HashSet()
        tags.add("news")
        val prefs = FeedPreferences(
            42, FeedPreferences.AutoDownloadSetting.ENABLED,
            true, FeedPreferences.AutoDeleteAction.ALWAYS, VolumeAdaptionSetting.OFF,
            "user", "pass", FeedFilter(), 1.5f, 0, 0, FeedPreferences.SkipSilence.AGGRESSIVE,
            false, FeedPreferences.NewEpisodesAction.ADD_TO_QUEUE, tags
        )

        val byteStream = ByteArrayOutputStream()
        ObjectOutputStream(byteStream).use { out -> out.writeObject(prefs) }
        val restored = ObjectInputStream(ByteArrayInputStream(byteStream.toByteArray())).use { input ->
            input.readObject() as FeedPreferences
        }

        assertEquals(prefs.getFeedID(), restored.getFeedID())
        assertEquals(prefs.getUsername(), restored.getUsername())
        assertEquals(prefs.getAutoDownload(), restored.getAutoDownload())
        assertEquals(prefs.getAutoDeleteAction(), restored.getAutoDeleteAction())
        assertEquals(prefs.tags, restored.tags)
    }
}
