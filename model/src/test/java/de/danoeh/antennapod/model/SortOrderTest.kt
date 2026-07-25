package de.danoeh.antennapod.model

import de.danoeh.antennapod.model.feed.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SortOrderTest {

    @Test
    fun fromCodeStringNullReturnsNull() {
        assertNull(SortOrder.fromCodeString(null))
    }

    @Test
    fun fromCodeStringEmptyReturnsNull() {
        assertNull(SortOrder.fromCodeString(""))
    }

    @Test
    fun fromCodeStringValidIntraFeedCode() {
        assertEquals(SortOrder.DATE_OLD_NEW, SortOrder.fromCodeString("1"))
    }

    @Test
    fun fromCodeStringValidInterFeedCode() {
        assertEquals(SortOrder.FEED_TITLE_A_Z, SortOrder.fromCodeString("101"))
    }

    @Test
    fun fromCodeStringUnknownCodeThrows() {
        assertThrows(IllegalArgumentException::class.java) { SortOrder.fromCodeString("999") }
    }

    @Test
    fun fromCodeStringNonNumericThrowsNumberFormatException() {
        assertThrows(NumberFormatException::class.java) { SortOrder.fromCodeString("not_a_number") }
    }

    @Test
    fun parseWithDefaultValidName() {
        assertEquals(SortOrder.RANDOM, SortOrder.parseWithDefault("RANDOM", SortOrder.DATE_NEW_OLD))
    }

    @Test
    fun parseWithDefaultInvalidNameReturnsDefault() {
        assertEquals(SortOrder.DATE_NEW_OLD, SortOrder.parseWithDefault("NOPE", SortOrder.DATE_NEW_OLD))
    }

    @Test
    fun toCodeStringNullReturnsNull() {
        assertNull(SortOrder.toCodeString(null))
    }

    @Test
    fun toCodeStringReturnsCode() {
        assertEquals("103", SortOrder.toCodeString(SortOrder.RANDOM))
    }

    @Test
    fun codeLiteralsPreserved() {
        assertEquals(1, SortOrder.DATE_OLD_NEW.code)
        assertEquals(11, SortOrder.GLOBAL_DEFAULT.code)
        assertEquals(101, SortOrder.FEED_TITLE_A_Z.code)
        assertEquals(106, SortOrder.COMPLETION_DATE_NEW_OLD.code)
    }
}
