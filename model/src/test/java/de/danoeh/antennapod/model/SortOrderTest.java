package de.danoeh.antennapod.model;

import de.danoeh.antennapod.model.feed.SortOrder;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class SortOrderTest {

    @Test
    public void fromCodeStringNullReturnsNull() {
        assertNull(SortOrder.fromCodeString(null));
    }

    @Test
    public void fromCodeStringEmptyReturnsNull() {
        assertNull(SortOrder.fromCodeString(""));
    }

    @Test
    public void fromCodeStringValidIntraFeedCode() {
        assertEquals(SortOrder.DATE_OLD_NEW, SortOrder.fromCodeString("1"));
    }

    @Test
    public void fromCodeStringValidInterFeedCode() {
        assertEquals(SortOrder.FEED_TITLE_A_Z, SortOrder.fromCodeString("101"));
    }

    @Test
    public void fromCodeStringUnknownCodeThrows() {
        assertThrows(IllegalArgumentException.class, () -> SortOrder.fromCodeString("999"));
    }

    @Test
    public void fromCodeStringNonNumericThrowsNumberFormatException() {
        assertThrows(NumberFormatException.class, () -> SortOrder.fromCodeString("not_a_number"));
    }

    @Test
    public void parseWithDefaultValidName() {
        assertEquals(SortOrder.RANDOM, SortOrder.parseWithDefault("RANDOM", SortOrder.DATE_NEW_OLD));
    }

    @Test
    public void parseWithDefaultInvalidNameReturnsDefault() {
        assertEquals(SortOrder.DATE_NEW_OLD, SortOrder.parseWithDefault("NOPE", SortOrder.DATE_NEW_OLD));
    }

    @Test
    public void toCodeStringNullReturnsNull() {
        assertNull(SortOrder.toCodeString(null));
    }

    @Test
    public void toCodeStringReturnsCode() {
        assertEquals("103", SortOrder.toCodeString(SortOrder.RANDOM));
    }

    @Test
    public void codeLiteralsPreserved() {
        assertEquals(1, SortOrder.DATE_OLD_NEW.code);
        assertEquals(11, SortOrder.GLOBAL_DEFAULT.code);
        assertEquals(101, SortOrder.FEED_TITLE_A_Z.code);
        assertEquals(106, SortOrder.COMPLETION_DATE_NEW_OLD.code);
    }
}
