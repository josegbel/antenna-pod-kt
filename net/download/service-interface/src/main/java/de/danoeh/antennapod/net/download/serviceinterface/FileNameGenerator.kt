package de.danoeh.antennapod.net.download.serviceinterface

import android.text.TextUtils
import androidx.annotation.VisibleForTesting
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import org.apache.commons.lang3.ArrayUtils
import org.apache.commons.lang3.StringUtils

/** Generates valid filenames for a given string. */
object FileNameGenerator {
    @VisibleForTesting
    const val MAX_FILENAME_LENGTH = 242 // limited by CircleCI
    private const val MD5_HEX_LENGTH = 32

    private val validChars =
        (
            "abcdefghijklmnopqrstuvwxyz" +
                "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
                "0123456789" +
                " _-"
            ).toCharArray()

    /**
     * This method will return a new string that doesn't contain any illegal
     * characters of the given string.
     */
    @JvmStatic
    fun generateFileName(string: String?): String {
        val sanitized = StringUtils.stripAccents(string)
        val buf = StringBuilder()
        for (c in sanitized) {
            if (Character.isSpaceChar(c) &&
                (buf.isEmpty() || Character.isSpaceChar(buf[buf.length - 1]))
            ) {
                continue
            }
            if (ArrayUtils.contains(validChars, c)) {
                buf.append(c)
            }
        }
        val filename = buf.toString().trim()
        return if (TextUtils.isEmpty(filename)) {
            randomString(8)
        } else if (filename.length >= MAX_FILENAME_LENGTH) {
            filename.substring(0, MAX_FILENAME_LENGTH - MD5_HEX_LENGTH - 1) + "_" + md5(filename)
        } else {
            filename
        }
    }

    private fun randomString(length: Int): String {
        val sb = StringBuilder(length)
        for (i in 0 until length) {
            sb.append(validChars[(Math.random() * validChars.size).toInt()])
        }
        return sb.toString()
    }

    private fun md5(md5: String): String? {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val array = md.digest(md5.toByteArray(Charsets.UTF_8))
            val sb = StringBuilder()
            for (b in array) {
                sb.append(Integer.toHexString((b.toInt() and 0xFF) or 0x100).substring(1, 3))
            }
            sb.toString()
        } catch (e: NoSuchAlgorithmException) {
            null
        }
    }
}
