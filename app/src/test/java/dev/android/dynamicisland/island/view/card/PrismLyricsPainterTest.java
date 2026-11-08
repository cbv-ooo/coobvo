package dev.android.dynamicisland.island.view.card;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import dev.android.dynamicisland.island.lyrics.MediaLyrics;
import org.junit.Test;

import java.util.List;

public class PrismLyricsPainterTest {
    @Test public void lineOnlyWordTimingMatchesPrismsWithWordsCalculation() {
        List<MediaLyrics.Word> words = PrismLyricsPainter.synthesizeWords("Hello world", 1000L, 6000L);
        assertEquals(2, words.size());
        assertEquals(1000L, words.get(0).startMs);
        assertEquals(1625L, words.get(0).endMs);
        assertEquals("Hello ", words.get(0).text);
        assertEquals(1625L, words.get(1).startMs);
        assertEquals(2250L, words.get(1).endMs);
        assertEquals("world", words.get(1).text);
    }

    @Test public void untimedLinesDoNotReceiveInventedWordTimings() {
        assertTrue(PrismLyricsPainter.synthesizeWords("Plain words", -1L, -1L).isEmpty());
        assertTrue(PrismLyricsPainter.synthesizeWords("Plain words", 0L, 0L).isEmpty());
    }
}
