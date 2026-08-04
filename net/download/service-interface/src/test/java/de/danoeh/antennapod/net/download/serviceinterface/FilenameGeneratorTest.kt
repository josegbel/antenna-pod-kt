package de.danoeh.antennapod.net.download.serviceinterface

import android.text.TextUtils
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.apache.commons.lang3.StringUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FilenameGeneratorTest {

    @Test
    fun testGenerateFileName() {
        val result = FileNameGenerator.generateFileName("abc abc")
        assertEquals(result, "abc abc")
        createFiles(result)
    }

    @Test
    fun testGenerateFileName1() {
        val result = FileNameGenerator.generateFileName("ab/c: <abc")
        assertEquals(result, "abc abc")
        createFiles(result)
    }

    @Test
    fun testGenerateFileName2() {
        val result = FileNameGenerator.generateFileName("abc abc ")
        assertEquals(result, "abc abc")
        createFiles(result)
    }

    @Test
    fun testFeedTitleContainsApostrophe() {
        val result = FileNameGenerator.generateFileName("Feed's Title ...")
        assertEquals("Feeds Title", result)
    }

    @Test
    fun testFeedTitleContainsDash() {
        val result = FileNameGenerator.generateFileName("Left - Right")
        assertEquals("Left - Right", result)
    }

    @Test
    fun testFeedTitleContainsAccents() {
        val result = FileNameGenerator.generateFileName("Äàáâãå")
        assertEquals("Aaaaaa", result)
    }

    @Test
    fun testInvalidInput() {
        val result = FileNameGenerator.generateFileName("???")
        assertFalse(TextUtils.isEmpty(result))
    }

    @Test
    fun testLongFilename() {
        val longName = StringUtils.repeat("x", 20 + FileNameGenerator.MAX_FILENAME_LENGTH)
        val result = FileNameGenerator.generateFileName(longName)
        assertTrue(result.length <= FileNameGenerator.MAX_FILENAME_LENGTH)
        createFiles(result)
    }

    @Test
    fun testLongFilenameNotEquals() {
        // Verify that the name is not just trimmed and different suffixes end up with the same name
        val longName = StringUtils.repeat("x", 20 + FileNameGenerator.MAX_FILENAME_LENGTH)
        val result1 = FileNameGenerator.generateFileName(longName + "a")
        val result2 = FileNameGenerator.generateFileName(longName + "b")
        assertNotEquals(result1, result2)
    }

    /**
     * Tests if files can be created.
     */
    private fun createFiles(name: String) {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.externalCacheDir
        val testFile = File(cache, name)
        assertTrue(testFile.mkdir())
        assertTrue(testFile.exists())
        assertTrue(testFile.delete())
        assertTrue(testFile.createNewFile())
    }
}
