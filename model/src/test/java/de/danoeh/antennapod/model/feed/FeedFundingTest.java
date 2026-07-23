package de.danoeh.antennapod.model.feed;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

public class FeedFundingTest {

    @Test
    public void extractPaymentLinksBlankReturnsNull() {
        assertNull(FeedFunding.extractPaymentLinks(null));
        assertNull(FeedFunding.extractPaymentLinks(""));
        assertNull(FeedFunding.extractPaymentLinks("   "));
    }

    @Test
    public void extractPaymentLinksOldFormatSingleLink() {
        ArrayList<FeedFunding> result = FeedFunding.extractPaymentLinks("http://example.com/donate");
        assertEquals(1, result.size());
        assertEquals("http://example.com/donate", result.get(0).url);
        assertEquals("", result.get(0).content);
    }

    @Test
    public void extractPaymentLinksNewFormatWithTitle() {
        String payLinks = "http://example.com/donate" + FeedFunding.FUNDING_TITLE_SEPARATOR + "Support the show";
        ArrayList<FeedFunding> result = FeedFunding.extractPaymentLinks(payLinks);
        assertEquals(1, result.size());
        assertEquals("http://example.com/donate", result.get(0).url);
        assertEquals("Support the show", result.get(0).content);
    }

    @Test
    public void extractPaymentLinksMultipleEntries() {
        String payLinks = "http://example.com/a" + FeedFunding.FUNDING_ENTRIES_SEPARATOR + "http://example.com/b";
        ArrayList<FeedFunding> result = FeedFunding.extractPaymentLinks(payLinks);
        assertEquals(2, result.size());
        assertEquals("http://example.com/a", result.get(0).url);
        assertEquals("", result.get(0).content);
        assertEquals("http://example.com/b", result.get(1).url);
        assertEquals("", result.get(1).content);
    }

    @Test
    public void extractPaymentLinksBlankUrlTokenSkipped() {
        String payLinks = "   " + FeedFunding.FUNDING_ENTRIES_SEPARATOR + "http://example.com/b";
        ArrayList<FeedFunding> result = FeedFunding.extractPaymentLinks(payLinks);
        assertEquals(1, result.size());
        assertEquals("http://example.com/b", result.get(0).url);
    }

    @Test
    public void extractPaymentLinksTrailingSeparatorMatchesJava() {
        String payLinks = "http://example.com/a" + FeedFunding.FUNDING_ENTRIES_SEPARATOR
                + "http://example.com/b" + FeedFunding.FUNDING_ENTRIES_SEPARATOR;
        ArrayList<FeedFunding> result = FeedFunding.extractPaymentLinks(payLinks);
        assertEquals(2, result.size());
        assertEquals("http://example.com/a", result.get(0).url);
        assertEquals("http://example.com/b", result.get(1).url);
    }

    @Test
    public void extractPaymentLinksOnlySeparatorsReturnsNull() {
        assertNull(FeedFunding.extractPaymentLinks(FeedFunding.FUNDING_ENTRIES_SEPARATOR));
        assertNull(FeedFunding.extractPaymentLinks(
                FeedFunding.FUNDING_ENTRIES_SEPARATOR + FeedFunding.FUNDING_ENTRIES_SEPARATOR));
    }

    @Test
    public void getPaymentLinksAsStringRoundTrip() {
        String payLinks = "http://example.com/a" + FeedFunding.FUNDING_TITLE_SEPARATOR + "Title A"
                + FeedFunding.FUNDING_ENTRIES_SEPARATOR + "http://example.com/b";
        ArrayList<FeedFunding> extracted = FeedFunding.extractPaymentLinks(payLinks);
        String serialized = FeedFunding.getPaymentLinksAsString(extracted);
        ArrayList<FeedFunding> roundTripped = FeedFunding.extractPaymentLinks(serialized);
        assertEquals(extracted, roundTripped);
    }

    @Test
    public void getPaymentLinksAsStringNullReturnsNull() {
        assertNull(FeedFunding.getPaymentLinksAsString(null));
    }

    @Test
    public void equalsBothNullFieldsEqual() {
        FeedFunding a = new FeedFunding(null, null);
        FeedFunding b = new FeedFunding(null, null);
        assertEquals(a, b);
    }

    @Test
    public void equalsSameUrlAndContentEqual() {
        FeedFunding a = new FeedFunding("http://example.com", "Thanks");
        FeedFunding b = new FeedFunding("http://example.com", "Thanks");
        assertEquals(a, b);
    }

    @Test
    public void equalsDifferentContentNotEqual() {
        FeedFunding a = new FeedFunding("http://example.com", "Thanks");
        FeedFunding b = new FeedFunding("http://example.com", "Other");
        assertNotEquals(a, b);
    }

    @Test
    public void hashCodeMatchesForEqualFunding() {
        FeedFunding a = new FeedFunding("http://example.com", "Thanks");
        FeedFunding b = new FeedFunding("http://example.com", "Thanks");
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void serializationRoundTripPreservesEquality() throws Exception {
        FeedFunding funding = new FeedFunding("http://example.com/donate", "Support the show");

        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(byteStream)) {
            out.writeObject(funding);
        }
        FeedFunding deserialized;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(byteStream.toByteArray()))) {
            deserialized = (FeedFunding) in.readObject();
        }

        assertEquals(funding, deserialized);
    }
}
