package de.danoeh.antennapod.model.feed

import java.util.ArrayList
import java.util.Arrays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ChapterTest {

    private fun createChapter(id: Long, start: Long): Chapter {
        val chapter = Chapter(start, "title", "link", "imageUrl")
        chapter.id = id
        return chapter
    }

    @Test
    fun equalsSameIdIsEqual() {
        val a = createChapter(1, 1000)
        val b = createChapter(1, 2000)
        assertEquals(a, b)
    }

    @Test
    fun equalsDifferentIdNotEqual() {
        val a = createChapter(1, 1000)
        val b = createChapter(2, 1000)
        assertNotEquals(a, b)
    }

    @Test
    fun equalsDifferentClassNotEqual() {
        val a = createChapter(1, 1000)
        assertNotEquals(a, "not a chapter")
    }

    @Test
    fun hashCodeMatchesForSameId() {
        val a = createChapter(1, 1000)
        val b = createChapter(1, 2000)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun getAfterPositionNullListReturnsMinusOne() {
        assertEquals(-1, Chapter.getAfterPosition(null, 5000))
    }

    @Test
    fun getAfterPositionEmptyListReturnsMinusOne() {
        assertEquals(-1, Chapter.getAfterPosition(ArrayList<Chapter>(), 5000))
    }

    @Test
    fun getAfterPositionBeforeFirstReturnsMinusOne() {
        val chapters = Arrays.asList(createChapter(1, 1000), createChapter(2, 2000))
        assertEquals(-1, Chapter.getAfterPosition(chapters, 500))
    }

    @Test
    fun getAfterPositionMidListReturnsPrevIndex() {
        val chapters = Arrays.asList(createChapter(1, 1000), createChapter(2, 2000), createChapter(3, 3000))
        assertEquals(1, Chapter.getAfterPosition(chapters, 2500))
    }

    @Test
    fun getAfterPositionPastLastReturnsLastIndex() {
        val chapters = Arrays.asList(createChapter(1, 1000), createChapter(2, 2000))
        assertEquals(chapters.size - 1, Chapter.getAfterPosition(chapters, 5000))
    }
}
