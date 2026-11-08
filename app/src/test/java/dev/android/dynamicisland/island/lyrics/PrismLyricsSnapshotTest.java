package dev.android.dynamicisland.island.lyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class PrismLyricsSnapshotTest {
    @Test public void importsPrismLyricsAndItsReturnedRomanizationAndTranslationWithoutChangingTimes() throws Exception {
        JSONObject snapshot = new JSONObject(
                "{\"source\":\"APPLE\",\"synced\":true,\"lines\":["
                + "{\"timeMs\":1000,\"endMs\":3000,\"text\":\"こんにちは\",\"words\":["
                + "{\"startMs\":1100,\"endMs\":1600,\"text\":\"こん\"},"
                + "{\"startMs\":1600,\"endMs\":2200,\"text\":\"にちは\"}],"
                + "\"background\":[{\"startMs\":1300,\"endMs\":1900,\"text\":\"(yeah)\"}],"
                + "\"isGap\":false,\"alignEnd\":true}],"
                + "\"romanizedLines\":[{\"timeMs\":1000,\"endMs\":3000,\"text\":\"konnichiwa\",\"words\":["
                + "{\"startMs\":1100,\"endMs\":1600,\"text\":\"konn\"},"
                + "{\"startMs\":1600,\"endMs\":2200,\"text\":\"ichiwa\"}]}],"
                + "\"translatedLines\":[\"hello there\"]}");
        MediaLyrics lyrics = MediaLyrics.fromPrismSnapshot(snapshot);
        assertEquals(MediaLyrics.Type.SYLLABLE, lyrics.type());
        assertEquals(1, lyrics.size());
        MediaLyrics.Line line = lyrics.line(0);
        assertEquals(1000L, line.startMs);
        assertEquals(3000L, line.endMs);
        assertEquals(1100L, line.words.get(0).startMs);
        assertEquals(1600L, line.words.get(0).endMs);
        assertEquals(1300L, line.background.get(0).startMs);
        assertTrue(line.alignEnd);
        assertEquals("konnichiwa", line.romanizedText);
        assertEquals(1600L, line.romanizedWords.get(1).startMs);
        assertEquals("hello there", line.translatedText);
        assertTrue(lyrics.hasRomanizedLines());
        assertTrue(lyrics.hasTranslatedLines());
    }

    @Test public void prismCurrentLineFollowsLatestStartEvenWhenAnEarlierLineOverlaps() throws Exception {
        JSONObject snapshot = new JSONObject(
                "{\"source\":\"APPLE\",\"synced\":true,\"lines\":["
                + "{\"timeMs\":1000,\"endMs\":5000,\"text\":\"lead\",\"words\":[],\"background\":[]},"
                + "{\"timeMs\":2500,\"endMs\":3800,\"text\":\"backing\",\"words\":[],\"background\":[]}]}");
        MediaLyrics lyrics = MediaLyrics.fromPrismSnapshot(snapshot);
        assertEquals(0, lyrics.prismCurrentIndex(1999));
        assertEquals(1, lyrics.prismCurrentIndex(2500));
        assertEquals(1, lyrics.prismCurrentIndex(4900));
        assertEquals(-1, lyrics.prismCurrentIndex(500));
    }

    @Test public void missingPrismRomanizationAndTranslationsRemainMissing() throws Exception {
        JSONObject snapshot = new JSONObject(
                "{\"source\":\"LRCLIB\",\"synced\":true,\"lines\":["
                + "{\"timeMs\":500,\"endMs\":1500,\"text\":\"Actual line\",\"words\":[],\"background\":[]}]}");
        MediaLyrics lyrics = MediaLyrics.fromPrismSnapshot(snapshot);
        assertEquals("Actual line", lyrics.line(0).text);
        assertEquals(null, lyrics.line(0).romanizedText);
        assertEquals(null, lyrics.line(0).translatedText);
        assertFalse(lyrics.hasRomanizedLines());
        assertFalse(lyrics.hasTranslatedLines());
    }
}
