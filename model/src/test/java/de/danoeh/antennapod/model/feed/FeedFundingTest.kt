package de.danoeh.antennapod.model.feed

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FeedFundingTest {

    @Test
    fun extractPaymentLinksBlankReturnsNull() {
        assertNull(FeedFunding.extractPaymentLinks(null))
        assertNull(FeedFunding.extractPaymentLinks(""))
        assertNull(FeedFunding.extractPaymentLinks("   "))
    }

    @Test
    fun extractPaymentLinksOldFormatSingleLink() {
        // extractPaymentLinks only returns null for a blank input (checked above); this literal is
        // non-blank, so the result is always non-null here.
        val result = FeedFunding.extractPaymentLinks("http://example.com/donate")!!
        assertEquals(1, result.size)
        assertEquals("http://example.com/donate", result.get(0).url)
        assertEquals("", result.get(0).content)
    }

    @Test
    fun extractPaymentLinksNewFormatWithTitle() {
        val payLinks = "http://example.com/donate" + FeedFunding.FUNDING_TITLE_SEPARATOR + "Support the show"
        // extractPaymentLinks only returns null for a blank input; payLinks is non-blank here.
        val result = FeedFunding.extractPaymentLinks(payLinks)!!
        assertEquals(1, result.size)
        assertEquals("http://example.com/donate", result.get(0).url)
        assertEquals("Support the show", result.get(0).content)
    }

    @Test
    fun extractPaymentLinksMultipleEntries() {
        val payLinks = "http://example.com/a" + FeedFunding.FUNDING_ENTRIES_SEPARATOR + "http://example.com/b"
        // extractPaymentLinks only returns null for a blank input; payLinks is non-blank here.
        val result = FeedFunding.extractPaymentLinks(payLinks)!!
        assertEquals(2, result.size)
        assertEquals("http://example.com/a", result.get(0).url)
        assertEquals("", result.get(0).content)
        assertEquals("http://example.com/b", result.get(1).url)
        assertEquals("", result.get(1).content)
    }

    @Test
    fun extractPaymentLinksBlankUrlTokenSkipped() {
        val payLinks = "   " + FeedFunding.FUNDING_ENTRIES_SEPARATOR + "http://example.com/b"
        // extractPaymentLinks only returns null for a blank input; payLinks is non-blank here.
        val result = FeedFunding.extractPaymentLinks(payLinks)!!
        assertEquals(1, result.size)
        assertEquals("http://example.com/b", result.get(0).url)
    }

    @Test
    fun extractPaymentLinksTrailingSeparatorMatchesJava() {
        val payLinks = "http://example.com/a" + FeedFunding.FUNDING_ENTRIES_SEPARATOR +
            "http://example.com/b" + FeedFunding.FUNDING_ENTRIES_SEPARATOR
        // extractPaymentLinks only returns null for a blank input; payLinks is non-blank here.
        val result = FeedFunding.extractPaymentLinks(payLinks)!!
        assertEquals(2, result.size)
        assertEquals("http://example.com/a", result.get(0).url)
        assertEquals("http://example.com/b", result.get(1).url)
    }

    @Test
    fun extractPaymentLinksOnlySeparatorsReturnsNull() {
        assertNull(FeedFunding.extractPaymentLinks(FeedFunding.FUNDING_ENTRIES_SEPARATOR))
        assertNull(
            FeedFunding.extractPaymentLinks(
                FeedFunding.FUNDING_ENTRIES_SEPARATOR + FeedFunding.FUNDING_ENTRIES_SEPARATOR
            )
        )
    }

    @Test
    fun getPaymentLinksAsStringRoundTrip() {
        val payLinks = "http://example.com/a" + FeedFunding.FUNDING_TITLE_SEPARATOR + "Title A" +
            FeedFunding.FUNDING_ENTRIES_SEPARATOR + "http://example.com/b"
        val extracted = FeedFunding.extractPaymentLinks(payLinks)
        val serialized = FeedFunding.getPaymentLinksAsString(extracted)
        val roundTripped = FeedFunding.extractPaymentLinks(serialized)
        assertEquals(extracted, roundTripped)
    }

    @Test
    fun getPaymentLinksAsStringNullReturnsNull() {
        assertNull(FeedFunding.getPaymentLinksAsString(null))
    }

    @Test
    fun extractPaymentLinksAlwaysYieldsNonNullContent() {
        val oldFormat = FeedFunding.extractPaymentLinks("http://example.com/donate")!!
        val titleFormat = FeedFunding.extractPaymentLinks(
            "http://example.com/donate" + FeedFunding.FUNDING_TITLE_SEPARATOR + "Support the show"
        )!!
        val multiEntry = FeedFunding.extractPaymentLinks(
            "http://example.com/a" + FeedFunding.FUNDING_ENTRIES_SEPARATOR + "http://example.com/b"
        )!!
        val blankTitleToken = FeedFunding.extractPaymentLinks(
            "http://example.com/a" + FeedFunding.FUNDING_TITLE_SEPARATOR + "   "
        )!!

        for (list in listOf(oldFormat, titleFormat, multiEntry, blankTitleToken)) {
            for (funding in list) {
                assertNotNull(funding.content)
            }
        }
    }

    @Test
    fun equalsBothNullFieldsEqual() {
        val a = FeedFunding(null, null)
        val b = FeedFunding(null, null)
        assertEquals(a, b)
    }

    @Test
    fun equalsSameUrlAndContentEqual() {
        val a = FeedFunding("http://example.com", "Thanks")
        val b = FeedFunding("http://example.com", "Thanks")
        assertEquals(a, b)
    }

    @Test
    fun equalsDifferentContentNotEqual() {
        val a = FeedFunding("http://example.com", "Thanks")
        val b = FeedFunding("http://example.com", "Other")
        assertNotEquals(a, b)
    }

    @Test
    fun hashCodeMatchesForEqualFunding() {
        val a = FeedFunding("http://example.com", "Thanks")
        val b = FeedFunding("http://example.com", "Thanks")
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun serializationRoundTripPreservesEquality() {
        val funding = FeedFunding("http://example.com/donate", "Support the show")

        val byteStream = ByteArrayOutputStream()
        ObjectOutputStream(byteStream).use { out -> out.writeObject(funding) }
        val deserialized = ObjectInputStream(ByteArrayInputStream(byteStream.toByteArray())).use { input ->
            input.readObject() as FeedFunding
        }

        assertEquals(funding, deserialized)
    }
}
