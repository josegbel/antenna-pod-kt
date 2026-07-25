package de.danoeh.antennapod.model.feed

import android.text.TextUtils
import de.danoeh.antennapod.model.playback.Playable
import java.util.Collections
import java.util.Objects
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.MockedStatic
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`

// android.text.TextUtils is an unmocked Android SDK stub in :model's bare-JVM unit tests
// (no Robolectric, per module policy). EmbeddedChapterImage.equals() calls TextUtils.equals()
// for the same-url/different-url paths, which throws "not mocked" if invoked for real here.
// A MockedStatic gives TextUtils.equals() its real AOSP semantics (null-tolerant CharSequence
// equality, which for two Strings is Objects.equals) so this test characterizes
// EmbeddedChapterImage's own logic against genuine device behavior rather than the stub's throw.
class EmbeddedChapterImageTest {

    private lateinit var textUtilsMock: MockedStatic<TextUtils>

    @Before
    fun setUp() {
        textUtilsMock = mockStatic(TextUtils::class.java)
        textUtilsMock.`when`<Boolean> { TextUtils.equals(any(), any()) }
            .thenAnswer { invocation -> Objects.equals(invocation.getArgument(0), invocation.getArgument(1)) }
    }

    @After
    fun tearDown() {
        textUtilsMock.close()
    }

    @Test
    fun makeUrlProducesExpectedFormat() {
        assertEquals("embedded-image://3/100", EmbeddedChapterImage.makeUrl(3, 100))
    }

    @Test
    fun constructorParsesPositionAndLength() {
        val image = EmbeddedChapterImage(null, "embedded-image://3/100")
        assertEquals(3, image.position)
        assertEquals(100, image.length)
        assertNull(image.media)
    }

    @Test
    fun constructorThrowsOnNonMatchingUrl() {
        assertThrows(IllegalArgumentException::class.java) { EmbeddedChapterImage(null, "http://x") }
    }

    @Test
    fun constructorRoundTripsWithMakeUrl() {
        val url = EmbeddedChapterImage.makeUrl(42, 7)
        val image = EmbeddedChapterImage(null, url)
        assertEquals(42, image.position)
        assertEquals(7, image.length)
    }

    @Test
    fun equalsSameUrlIsEqual() {
        val a = EmbeddedChapterImage(null, "embedded-image://3/100")
        val b = EmbeddedChapterImage(null, "embedded-image://3/100")
        assertEquals(a, b)
    }

    @Test
    fun equalsDifferentUrlNotEqual() {
        val a = EmbeddedChapterImage(null, "embedded-image://3/100")
        val b = EmbeddedChapterImage(null, "embedded-image://4/100")
        assertNotEquals(a, b)
    }

    @Test
    fun equalsDifferentClassNotEqual() {
        val a = EmbeddedChapterImage(null, "embedded-image://3/100")
        assertNotEquals(a, "embedded-image://3/100")
    }

    @Test
    fun equalsSameInstance() {
        val a = EmbeddedChapterImage(null, "embedded-image://3/100")
        assertEquals(a, a)
    }

    @Test
    fun hashCodeMatchesForSameUrl() {
        val a = EmbeddedChapterImage(null, "embedded-image://3/100")
        val b = EmbeddedChapterImage(null, "embedded-image://3/100")
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun getModelForReturnsInstanceForEmbeddedUrl() {
        val chapter = Chapter(0, "title", "link", "embedded-image://3/100")
        val media = mock(Playable::class.java)
        `when`(media.chapters).thenReturn(Collections.singletonList(chapter))

        val model = EmbeddedChapterImage.getModelFor(media, 0)

        assertTrue(model is EmbeddedChapterImage)
        assertEquals(3, (model as EmbeddedChapterImage).position)
        assertEquals(100, (model as EmbeddedChapterImage).length)
    }

    @Test
    fun getModelForReturnsRawStringForNonEmbeddedUrl() {
        val chapter = Chapter(0, "title", "link", "http://example.com/image.png")
        val media = mock(Playable::class.java)
        `when`(media.chapters).thenReturn(Collections.singletonList(chapter))

        val model = EmbeddedChapterImage.getModelFor(media, 0)

        assertEquals("http://example.com/image.png", model)
    }

    // Pins the Java-equivalent throw site for a null imageUrl: the original Java NPEs
    // from inside Pattern.matcher()/Matcher's constructor (java.util.regex), with the
    // JDK's helpful-NPE message identifying the CharSequence access. A `!!` assertion at
    // extraction time (the bug red-team caught) throws one call-frame earlier, from
    // EmbeddedChapterImage.getModelFor itself, with no message at all - this test fails
    // against that shape and only passes for the Java-equivalent throw site/message.
    @Test
    fun getModelForNullImageUrlThrowsFromInsideMatcherNotAtExtraction() {
        val chapter = Chapter(0, "title", "link", null)
        val media = mock(Playable::class.java)
        `when`(media.chapters).thenReturn(Collections.singletonList(chapter))

        val exception = assertThrows(NullPointerException::class.java) { EmbeddedChapterImage.getModelFor(media, 0) }

        assertNotNull(exception.message)
        assertTrue(exception.stackTrace[0].className.startsWith("java.util.regex"))
    }

    // Regression guard for the post-conversion chapters!! shape (milestone 6): pins that a null
    // Playable.chapters still throws NullPointerException, from inside EmbeddedChapterImage
    // itself (not swallowed by a future `?.` rewrite, and not laundered through a Java boundary
    // that would produce a message again). Mirrors getModelForNullImageUrlThrowsFromInsideMatcherNotAtExtraction
    // above for the analogous imageUrl case.
    @Test
    fun getModelForNullChaptersThrowsNpe() {
        val media = mock(Playable::class.java)
        `when`(media.chapters).thenReturn(null)

        val exception = assertThrows(NullPointerException::class.java) { EmbeddedChapterImage.getModelFor(media, 0) }

        assertNull(exception.message)
        // Real throw site is EmbeddedChapterImage$Companion (the @JvmStatic bridge on the outer
        // class delegates to the companion object's method body, where the !! check lives) -
        // still "inside EmbeddedChapterImage", not laundered through any other class.
        assertTrue(
            exception.stackTrace[0].className.startsWith(
                "de.danoeh.antennapod.model.feed.EmbeddedChapterImage"
            )
        )
    }
}
