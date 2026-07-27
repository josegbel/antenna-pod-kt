package de.danoeh.antennapod.net.download.serviceinterface;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class FileNameGeneratorCharacterizationTest {

    private static final String VALID_CHARS =
            "abcdefghijklmnopqrstuvwxyz"
            + "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
            + "0123456789"
            + " _-";

    private static String md5Suffix(String input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] array = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : array) {
            sb.append(Integer.toHexString((b & 0xFF) | 0x100).substring(1, 3));
        }
        return sb.toString();
    }

    @Test
    public void generateFileNameAt241CharsIsNotHashed() {
        String input = StringUtils.repeat("a", 241);
        String result = FileNameGenerator.generateFileName(input);
        assertEquals(input, result);
    }

    @Test
    public void generateFileNameAt242CharsIsHashedWithExactMd5Suffix() throws NoSuchAlgorithmException {
        String input = StringUtils.repeat("a", 242);
        String result = FileNameGenerator.generateFileName(input);

        String expectedPrefix = input.substring(0, FileNameGenerator.MAX_FILENAME_LENGTH - 32 - 1);
        String expectedSuffix = md5Suffix(input);

        assertEquals(expectedPrefix + "_" + expectedSuffix, result);
    }

    @Test
    public void generateFileNameForAllInvalidCharsReturnsRandomFallbackOfLength8() {
        String result = FileNameGenerator.generateFileName("???");
        assertEquals(8, result.length());
        for (char c : result.toCharArray()) {
            assertTrue(VALID_CHARS.indexOf(c) >= 0);
        }
    }

    @Test
    public void generateFileNameCollapsesLeadingSpaces() {
        String result = FileNameGenerator.generateFileName("   abc");
        assertEquals("abc", result);
    }

    @Test
    public void generateFileNameTreatsTabAndNonBreakingSpaceDifferently() {
        assertEquals("ab", FileNameGenerator.generateFileName("a\tb"));
        assertEquals("a b", FileNameGenerator.generateFileName("a b"));
    }

    @Test
    public void generateFileNameNullThrowsNpe() {
        assertThrows(NullPointerException.class, () -> FileNameGenerator.generateFileName(null));
    }
}
