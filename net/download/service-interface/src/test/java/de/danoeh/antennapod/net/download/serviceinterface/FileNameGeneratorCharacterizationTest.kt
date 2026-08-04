package de.danoeh.antennapod.net.download.serviceinterface

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.apache.commons.lang3.StringUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FileNameGeneratorCharacterizationTest {

    @Test
    fun generateFileNameAt241CharsIsNotHashed() {
        val input = StringUtils.repeat("a", 241)
        val result = FileNameGenerator.generateFileName(input)
        assertEquals(input, result)
    }

    @Test
    fun generateFileNameAt242CharsIsHashedWithExactMd5Suffix() {
        val input = StringUtils.repeat("a", 242)
        val result = FileNameGenerator.generateFileName(input)

        val expectedPrefix = input.substring(0, FileNameGenerator.MAX_FILENAME_LENGTH - 32 - 1)
        val expectedSuffix = md5Suffix(input)

        assertEquals(expectedPrefix + "_" + expectedSuffix, result)
    }

    @Test
    fun generateFileNameForAllInvalidCharsReturnsRandomFallbackOfLength8() {
        val result = FileNameGenerator.generateFileName("???")
        assertEquals(8, result.length)
        for (c in result.toCharArray()) {
            assertTrue(VALID_CHARS.indexOf(c) >= 0)
        }
    }

    @Test
    fun generateFileNameCollapsesLeadingSpaces() {
        val result = FileNameGenerator.generateFileName("   abc")
        assertEquals("abc", result)
    }

    @Test
    fun generateFileNameTreatsTabAndNonBreakingSpaceDifferently() {
        assertEquals("ab", FileNameGenerator.generateFileName("a\tb"))
        assertEquals("a b", FileNameGenerator.generateFileName("a b"))
    }

    @Test
    fun generateFileNameNullThrowsNpe() {
        assertThrows(NullPointerException::class.java) { FileNameGenerator.generateFileName(null) }
    }

    private companion object {
        private const val VALID_CHARS =
            "abcdefghijklmnopqrstuvwxyz" +
                "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
                "0123456789" +
                " _-"

        private fun md5Suffix(input: String): String {
            val md = MessageDigest.getInstance("MD5")
            val array = md.digest(input.toByteArray(StandardCharsets.UTF_8))
            val sb = StringBuilder()
            for (b in array) {
                sb.append(Integer.toHexString((b.toInt() and 0xFF) or 0x100).substring(1, 3))
            }
            return sb.toString()
        }
    }
}
