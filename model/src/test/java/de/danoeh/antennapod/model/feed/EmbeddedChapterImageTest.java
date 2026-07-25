package de.danoeh.antennapod.model.feed;

import android.text.TextUtils;

import de.danoeh.antennapod.model.playback.Playable;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.Collections;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

// android.text.TextUtils is an unmocked Android SDK stub in :model's bare-JVM unit tests
// (no Robolectric, per module policy). EmbeddedChapterImage.equals() calls TextUtils.equals()
// for the same-url/different-url paths, which throws "not mocked" if invoked for real here.
// A MockedStatic gives TextUtils.equals() its real AOSP semantics (null-tolerant CharSequence
// equality, which for two Strings is Objects.equals) so this test characterizes
// EmbeddedChapterImage's own logic against genuine device behavior rather than the stub's throw.
public class EmbeddedChapterImageTest {

    private MockedStatic<TextUtils> textUtilsMock;

    @Before
    public void setUp() {
        textUtilsMock = mockStatic(TextUtils.class);
        textUtilsMock.when(() -> TextUtils.equals(any(), any()))
                .thenAnswer(invocation -> Objects.equals(invocation.getArgument(0), invocation.getArgument(1)));
    }

    @After
    public void tearDown() {
        textUtilsMock.close();
    }

    @Test
    public void makeUrlProducesExpectedFormat() {
        assertEquals("embedded-image://3/100", EmbeddedChapterImage.makeUrl(3, 100));
    }

    @Test
    public void constructorParsesPositionAndLength() {
        EmbeddedChapterImage image = new EmbeddedChapterImage(null, "embedded-image://3/100");
        assertEquals(3, image.getPosition());
        assertEquals(100, image.getLength());
        assertNull(image.getMedia());
    }

    @Test
    public void constructorThrowsOnNonMatchingUrl() {
        assertThrows(IllegalArgumentException.class, () -> new EmbeddedChapterImage(null, "http://x"));
    }

    @Test
    public void constructorRoundTripsWithMakeUrl() {
        String url = EmbeddedChapterImage.makeUrl(42, 7);
        EmbeddedChapterImage image = new EmbeddedChapterImage(null, url);
        assertEquals(42, image.getPosition());
        assertEquals(7, image.getLength());
    }

    @Test
    public void equalsSameUrlIsEqual() {
        EmbeddedChapterImage a = new EmbeddedChapterImage(null, "embedded-image://3/100");
        EmbeddedChapterImage b = new EmbeddedChapterImage(null, "embedded-image://3/100");
        assertEquals(a, b);
    }

    @Test
    public void equalsDifferentUrlNotEqual() {
        EmbeddedChapterImage a = new EmbeddedChapterImage(null, "embedded-image://3/100");
        EmbeddedChapterImage b = new EmbeddedChapterImage(null, "embedded-image://4/100");
        assertNotEquals(a, b);
    }

    @Test
    public void equalsDifferentClassNotEqual() {
        EmbeddedChapterImage a = new EmbeddedChapterImage(null, "embedded-image://3/100");
        assertNotEquals(a, "embedded-image://3/100");
    }

    @Test
    public void equalsSameInstance() {
        EmbeddedChapterImage a = new EmbeddedChapterImage(null, "embedded-image://3/100");
        assertEquals(a, a);
    }

    @Test
    public void hashCodeMatchesForSameUrl() {
        EmbeddedChapterImage a = new EmbeddedChapterImage(null, "embedded-image://3/100");
        EmbeddedChapterImage b = new EmbeddedChapterImage(null, "embedded-image://3/100");
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void getModelForReturnsInstanceForEmbeddedUrl() {
        Chapter chapter = new Chapter(0, "title", "link", "embedded-image://3/100");
        Playable media = mock(Playable.class);
        when(media.getChapters()).thenReturn(Collections.singletonList(chapter));

        Object model = EmbeddedChapterImage.getModelFor(media, 0);

        assertTrue(model instanceof EmbeddedChapterImage);
        assertEquals(3, ((EmbeddedChapterImage) model).getPosition());
        assertEquals(100, ((EmbeddedChapterImage) model).getLength());
    }

    @Test
    public void getModelForReturnsRawStringForNonEmbeddedUrl() {
        Chapter chapter = new Chapter(0, "title", "link", "http://example.com/image.png");
        Playable media = mock(Playable.class);
        when(media.getChapters()).thenReturn(Collections.singletonList(chapter));

        Object model = EmbeddedChapterImage.getModelFor(media, 0);

        assertEquals("http://example.com/image.png", model);
    }

    // Pins the Java-equivalent throw site for a null imageUrl: the original Java NPEs
    // from inside Pattern.matcher()/Matcher's constructor (java.util.regex), with the
    // JDK's helpful-NPE message identifying the CharSequence access. A `!!` assertion at
    // extraction time (the bug red-team caught) throws one call-frame earlier, from
    // EmbeddedChapterImage.getModelFor itself, with no message at all - this test fails
    // against that shape and only passes for the Java-equivalent throw site/message.
    @Test
    public void getModelForNullImageUrlThrowsFromInsideMatcherNotAtExtraction() {
        Chapter chapter = new Chapter(0, "title", "link", null);
        Playable media = mock(Playable.class);
        when(media.getChapters()).thenReturn(Collections.singletonList(chapter));

        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> EmbeddedChapterImage.getModelFor(media, 0));

        assertNotNull(exception.getMessage());
        assertTrue(exception.getStackTrace()[0].getClassName().startsWith("java.util.regex"));
    }

    // Regression guard for the post-conversion chapters!! shape (milestone 6): pins that a null
    // Playable.chapters still throws NullPointerException, from inside EmbeddedChapterImage
    // itself (not swallowed by a future `?.` rewrite, and not laundered through a Java boundary
    // that would produce a message again). Mirrors getModelForNullImageUrlThrowsFromInsideMatcherNotAtExtraction
    // above for the analogous imageUrl case.
    @Test
    public void getModelForNullChaptersThrowsNpe() {
        Playable media = mock(Playable.class);
        when(media.getChapters()).thenReturn(null);

        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> EmbeddedChapterImage.getModelFor(media, 0));

        assertNull(exception.getMessage());
        // Real throw site is EmbeddedChapterImage$Companion (the @JvmStatic bridge on the outer
        // class delegates to the companion object's method body, where the !! check lives) -
        // still "inside EmbeddedChapterImage", not laundered through any other class.
        assertTrue(exception.getStackTrace()[0].getClassName().startsWith("de.danoeh.antennapod.model.feed.EmbeddedChapterImage"));
    }
}
