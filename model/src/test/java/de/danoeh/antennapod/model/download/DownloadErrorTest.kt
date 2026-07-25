package de.danoeh.antennapod.model.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DownloadErrorTest {

    @Test
    fun testGetCodePerConstant() {
        assertEquals(0, DownloadError.SUCCESS.getCode())
        assertEquals(1, DownloadError.ERROR_PARSER_EXCEPTION.getCode())
        assertEquals(2, DownloadError.ERROR_UNSUPPORTED_TYPE.getCode())
        assertEquals(3, DownloadError.ERROR_CONNECTION_ERROR.getCode())
        assertEquals(4, DownloadError.ERROR_MALFORMED_URL.getCode())
        assertEquals(5, DownloadError.ERROR_IO_ERROR.getCode())
        assertEquals(6, DownloadError.ERROR_FILE_EXISTS.getCode())
        assertEquals(7, DownloadError.ERROR_DOWNLOAD_CANCELLED.getCode())
        assertEquals(8, DownloadError.ERROR_DEVICE_NOT_FOUND.getCode())
        assertEquals(9, DownloadError.ERROR_HTTP_DATA_ERROR.getCode())
        assertEquals(10, DownloadError.ERROR_NOT_ENOUGH_SPACE.getCode())
        assertEquals(11, DownloadError.ERROR_UNKNOWN_HOST.getCode())
        assertEquals(12, DownloadError.ERROR_REQUEST_ERROR.getCode())
        assertEquals(13, DownloadError.ERROR_DB_ACCESS_ERROR.getCode())
        assertEquals(14, DownloadError.ERROR_UNAUTHORIZED.getCode())
        assertEquals(15, DownloadError.ERROR_FILE_TYPE.getCode())
        assertEquals(16, DownloadError.ERROR_FORBIDDEN.getCode())
        assertEquals(17, DownloadError.ERROR_IO_WRONG_SIZE.getCode())
        assertEquals(18, DownloadError.ERROR_IO_BLOCKED.getCode())
        assertEquals(19, DownloadError.ERROR_UNSUPPORTED_TYPE_HTML.getCode())
        assertEquals(20, DownloadError.ERROR_NOT_FOUND.getCode())
        assertEquals(21, DownloadError.ERROR_CERTIFICATE.getCode())
        assertEquals(22, DownloadError.ERROR_PARSER_EXCEPTION_DUPLICATE.getCode())
    }

    @Test
    fun testFromCodeRoundTrip() {
        for (error in DownloadError.entries) {
            assertEquals(error, DownloadError.fromCode(error.getCode()))
        }
    }

    @Test
    fun testFromCodeUnknownThrows() {
        assertThrows(IllegalArgumentException::class.java) { DownloadError.fromCode(9999) }
        assertThrows(IllegalArgumentException::class.java) { DownloadError.fromCode(-1) }
    }
}
