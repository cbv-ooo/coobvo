package dev.android.dynamicisland.island.view.card;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PrismPlayerPreferencesTest {
    @Test public void readsTheNestedPlayerPreferencesExportedByTheBridge() {
        PrismPlayerPreferences prefs = PrismPlayerPreferences.fromLyricsLook(
                "{\"playerPreferences\":{"
                        + "\"playerTimes\":\"LEFT\",\"showTrackLength\":true,"
                        + "\"scrubberStyle\":\"THUMB\",\"scrubberColor\":\"COVER\","
                        + "\"scrubberThumb\":\"AUTO\",\"scrubberThickness\":1.5,"
                        + "\"playerTitleAlign\":\"CENTER\",\"playerTitleSize\":\"LARGE\","
                        + "\"playerTitleScroll\":false,\"playerArtCorners\":26,"
                        + "\"shapes\":{\"player\":\"CIRCLE\"},"
                        + "\"type\":{\"playerTitleWeight\":\"BOLD\"}}}");

        assertTrue(prefs.showRightTime());
        assertFalse(prefs.showElapsedTime());
        assertFalse(prefs.showRemainingTime());
        assertTrue(prefs.showTrackLength);
        assertEquals("DOT", prefs.effectiveThumb());
        assertEquals("COVER", prefs.scrubberColor);
        assertEquals(1.5f, prefs.scrubberThickness, 0.001f);
        assertEquals("CENTER", prefs.titleAlign);
        assertEquals(1.2f, prefs.titleScale(), 0.001f);
        assertEquals("BOLD", prefs.titleWeight);
        assertFalse(prefs.titleScroll);
        assertEquals(26, prefs.artCornersDp);
        assertEquals("CIRCLE", prefs.coverShape);
    }

    @Test public void readsLegacyFlattenedFieldsAndResolvesAutoThumbByStyle() {
        PrismPlayerPreferences prefs = PrismPlayerPreferences.fromLyricsLook(
                "{\"playerTimes\":\"ELAPSED\",\"scrubberStyle\":\"RIBBONS\","
                        + "\"scrubberThumb\":\"AUTO\",\"playerTitleSize\":\"SMALL\"}");

        assertTrue(prefs.showElapsedTime());
        assertFalse(prefs.showRightTime());
        assertEquals("DOT", prefs.effectiveThumb());
        assertEquals(0.82f, prefs.titleScale(), 0.001f);
    }

    @Test public void defaultsAreSafeAndInvalidValuesAreIgnored() {
        PrismPlayerPreferences defaults = PrismPlayerPreferences.fromLyricsLook(null);
        assertTrue(defaults.showElapsedTime());
        assertTrue(defaults.showRightTime());
        assertEquals("NONE", defaults.effectiveThumb());
        assertEquals(20, defaults.artCornersDp);
        assertEquals("ROUNDED", defaults.coverShape);

        PrismPlayerPreferences invalid = PrismPlayerPreferences.fromLyricsLook(
                "{\"playerPreferences\":{\"playerTimes\":\"BROKEN\","
                        + "\"scrubberThickness\":9,\"playerArtCorners\":-4}}");
        assertEquals(PrismPlayerPreferences.TIME_BOTH, invalid.playerTimes);
        assertEquals(2.0f, invalid.scrubberThickness, 0.001f);
        assertEquals(0, invalid.artCornersDp);
    }
}
