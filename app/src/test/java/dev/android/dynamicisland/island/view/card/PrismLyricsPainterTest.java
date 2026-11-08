package dev.android.dynamicisland.island.view.card;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import dev.android.dynamicisland.island.lyrics.MediaLyrics;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

public class PrismLyricsPainterTest {

    @Test public void islandLyricsUseLargerTextAndReadableTwoLineHistory() {
        assertEquals(17.0f, PrismLyricsPainter.mainTextSizeDp(null), 0.001f);
        assertEquals(20.0f, PrismLyricsPainter.lineHeightDp(null), 0.001f);
        assertEquals(0.48f, PrismLyricsPainter.historyLineAlpha(0, 0.0f), 0.001f);
        assertEquals(0.70f, PrismLyricsPainter.historyLineAlpha(0, 1.0f), 0.001f);
        assertEquals(0.36f, PrismLyricsPainter.historyLineAlpha(1, 0.5f), 0.001f);
    }

    @Test public void globalLyricsScaleMultipliesPresetAndAppTextScale() throws Exception {
        JSONObject look = new JSONObject("{\"sizeScale\":1.1,\"textScale\":1.2,\"lyricsScale\":1.3}");
        assertEquals(17.0f * 1.1f * 1.2f * 1.3f,
                PrismLyricsPainter.mainTextSizeDp(look), 0.001f);
    }

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

    @Test public void lyricHistoryKeepsTheTwoLinesBeforeTheCurrentLine() {
        PrismLyricsPainter.State state = new PrismLyricsPainter.State();
        state.update(0, "first", 1000L);
        state.update(1, "second", 2000L);
        state.update(2, "third", 3000L);
        state.update(3, "fourth", 4000L);

        assertEquals(2, state.pastLines.size());
        assertEquals("third", state.pastLines.get(0));
        assertEquals("second", state.pastLines.get(1));
        assertEquals("fourth", state.text);
    }

    @Test public void lyricLineChangesUsePrisms340msFadeAndSlideWindow() {
        PrismLyricsPainter.State state = new PrismLyricsPainter.State();
        assertEquals(1.0f, state.update(0, "first line", 1000L), 0.001f);
        assertEquals(0.0f, state.update(1, "second line", 1100L), 0.001f);
        assertEquals("first line", state.previousText);
        assertEquals(0.5f, state.update(1, "second line", 1270L), 0.001f);
        assertEquals(1.0f, state.update(1, "second line", 1440L), 0.001f);
        assertEquals(null, state.previousText);
    }
}
