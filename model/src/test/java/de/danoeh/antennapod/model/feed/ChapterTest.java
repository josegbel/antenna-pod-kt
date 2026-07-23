package de.danoeh.antennapod.model.feed;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class ChapterTest {

    private Chapter createChapter(long id, long start) {
        Chapter chapter = new Chapter(start, "title", "link", "imageUrl");
        chapter.setId(id);
        return chapter;
    }

    @Test
    public void equalsSameIdIsEqual() {
        Chapter a = createChapter(1, 1000);
        Chapter b = createChapter(1, 2000);
        assertEquals(a, b);
    }

    @Test
    public void equalsDifferentIdNotEqual() {
        Chapter a = createChapter(1, 1000);
        Chapter b = createChapter(2, 1000);
        assertNotEquals(a, b);
    }

    @Test
    public void equalsDifferentClassNotEqual() {
        Chapter a = createChapter(1, 1000);
        assertNotEquals(a, "not a chapter");
    }

    @Test
    public void hashCodeMatchesForSameId() {
        Chapter a = createChapter(1, 1000);
        Chapter b = createChapter(1, 2000);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void getAfterPositionNullListReturnsMinusOne() {
        assertEquals(-1, Chapter.getAfterPosition(null, 5000));
    }

    @Test
    public void getAfterPositionEmptyListReturnsMinusOne() {
        assertEquals(-1, Chapter.getAfterPosition(new ArrayList<>(), 5000));
    }

    @Test
    public void getAfterPositionBeforeFirstReturnsMinusOne() {
        List<Chapter> chapters = Arrays.asList(createChapter(1, 1000), createChapter(2, 2000));
        assertEquals(-1, Chapter.getAfterPosition(chapters, 500));
    }

    @Test
    public void getAfterPositionMidListReturnsPrevIndex() {
        List<Chapter> chapters = Arrays.asList(createChapter(1, 1000), createChapter(2, 2000), createChapter(3, 3000));
        assertEquals(1, Chapter.getAfterPosition(chapters, 2500));
    }

    @Test
    public void getAfterPositionPastLastReturnsLastIndex() {
        List<Chapter> chapters = Arrays.asList(createChapter(1, 1000), createChapter(2, 2000));
        assertEquals(chapters.size() - 1, Chapter.getAfterPosition(chapters, 5000));
    }
}
