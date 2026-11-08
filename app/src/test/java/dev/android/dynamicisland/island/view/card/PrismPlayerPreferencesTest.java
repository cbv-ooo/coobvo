package dev.android.dynamicisland.island.view.card;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class PrismPlayerPreferencesTest {
    @Test public void readsTheNestedPlayerPreferencesExportedByTheBridge() {
        PrismPlayerPreferences prefs = PrismPlayerPreferences.fromLyricsLook(
                "{\"playerPreferences\":{"
                        + "\"playerTimes\":\"LEFT\",\"showTrackLength\":true,"
                        + "\"scrubberStyle\":\"THUMB\",\"scrubberColor\":\"COVER\","
                        + "\"scrubberThumb\":\"AUTO\",\"scrubberThickness\":1.5,"
                        + "\"playerTitleAlign\":\"CENTER\",\"playerTitleSize\":\"LARGE\","
                        + "\"playerTitleScroll\":false,\"playerArtCorners\":26,\"coverSize\":\"FULL\","
                        + "\"shapes\":{\"player\":\"CIRCLE\"},"
                        + "\"typeSpacingEm\":0.03,"
                        + "\"type\":{\"playerTitleWeight\":\"BOLD\",\"weight\":\"LIGHT\"}}}");

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
        assertEquals(700, prefs.titleFontWeight());
        assertEquals(600, prefs.artistFontWeight());
        assertEquals(0.03f, prefs.typeSpacingEm, 0.001f);
        assertFalse(prefs.titleScroll);
        assertEquals(26, prefs.artCornersDp);
        assertEquals("FULL", prefs.coverSize);
        assertEquals("CIRCLE", prefs.coverShape);
    }

    @Test public void readsPlayerLayoutDeckButtonsBackgroundVolumeAndEffects() {
        PrismPlayerPreferences prefs = PrismPlayerPreferences.fromLyricsLook(
                "{\"playerPreferences\":{"
                        + "\"playerLayout\":\"VINYL\",\"playerBackground\":\"BLUR\","
                        + "\"playerDeck\":[\"SCRUBBER\",\"TRANSPORT\",\"VOLUME\",\"BUTTONS\"],"
                        + "\"playerButtons\":[\"LIKE\",\"QUEUE\"],\"hiddenParts\":[\"VOLUME\"],"
                        + "\"hideVolumeBar\":false,\"volumeControl\":\"PRISM\",\"volumeMatchesProgress\":true,"
                        + "\"fullBleedArtwork\":true,\"canvasEnabled\":true,\"canvasFillsBackground\":true,"
                        + "\"lyricsOnPlayer\":false,\"transportStyle\":\"OUTLINE\","
                        + "\"visualizer\":\"WAVE\",\"visualizerColor\":\"ACCENT\","
                        + "\"showOutputDevice\":false,\"crossfadeMark\":true,"
                        + "\"view\":{\"buttonsPill\":true,\"crossfadeMark\":true,\"crossfadeShape\":true},"
                        + "\"shapes\":{\"button\":\"SQUARE\"}}}");

        assertEquals("VINYL", prefs.layout);
        assertEquals("BLUR", prefs.background);
        assertEquals("OUTLINE", prefs.transportStyle);
        assertEquals("SQUARE", prefs.buttonShape);
        assertTrue(prefs.buttonsPill);
        assertTrue(prefs.hasDeckPart("SCRUBBER"));
        assertTrue(prefs.hasDeckPart("BUTTONS"));
        assertFalse(prefs.hasDeckPart("VOLUME"));
        assertTrue(prefs.hasPlayerButton("LIKE"));
        assertTrue(prefs.volumeMatchesProgress);
        assertEquals("PRISM", prefs.volumeControl);
        assertTrue(prefs.fullBleedArtwork);
        assertTrue(prefs.canvasEnabled);
        assertTrue(prefs.canvasFillsBackground);
        assertFalse(prefs.lyricsOnPlayer);
        assertFalse(prefs.showOutputDevice);
        assertTrue(prefs.crossfadeMark);
        assertTrue(prefs.crossfadeShape);
        assertEquals("WAVE", prefs.visualizer);
        assertEquals("ACCENT", prefs.visualizerColor);
    }



    @Test public void readsRemainingPlayerLookAndWhatsOnPlayerPreferences() {
        PrismPlayerPreferences prefs = PrismPlayerPreferences.fromLyricsLook(
                "{\"playerPreferences\":{"
                        + "\"playerDeck\":[\"STRIP\",\"SCRUBBER\",\"TRANSPORT\",\"VOLUME\"],"
                        + "\"playerButtons\":[\"LIKE\",\"VIDEO\",\"QUEUE\"],\"showVideoSwitch\":false,"
                        + "\"playerBackground\":\"GRADIENT\",\"playerBlur\":1.3,\"backdropDim\":0.42,"
                        + "\"meshSpeed\":0.0,\"meshIntensity\":1.4,\"canvasEnabled\":true,"
                        + "\"canvasOnCellular\":true,\"canvasFillsBackground\":true,"
                        + "\"artShrinkOnPause\":false,\"vinylTonearm\":false,"
                        + "\"hideStatusBarPlayer\":true,\"playerHandle\":false,\"playerNavBand\":true,"
                        + "\"bestPartButton\":false,\"playerShuffleRepeat\":false,"
                        + "\"sessionButtons\":[\"LIKE\",\"RADIO\"],"
                        + "\"view\":{\"gradientDepth\":0.8,\"blendCovers\":true},"
                        + "\"hiddenParts\":[\"COVER\",\"TITLE\",\"ARTIST\",\"PREV_NEXT\",\"PROGRESS\",\"LYRIC_LINE\"]}}");

        assertFalse(prefs.showsPart("COVER"));
        assertFalse(prefs.showsPart("TITLE"));
        assertFalse(prefs.showsPart("ARTIST"));
        assertFalse(prefs.showsPart("PREV_NEXT"));
        assertFalse(prefs.showsPart("PROGRESS"));
        assertFalse(prefs.showsPart("LYRIC_LINE"));
        assertFalse(prefs.showsPart("HANDLE"));
        assertTrue(prefs.showsPart("NAV_BAND"));
        assertFalse(prefs.showsPart("BEST_PART"));
        assertFalse(prefs.showsPart("SHUFFLE_REPEAT"));
        assertTrue(prefs.showsPart("BLEND_COVERS"));
        assertTrue(prefs.hasSessionButton("RADIO"));
        assertFalse(prefs.hasPlayerButton("VIDEO"));
        assertEquals(1.3f, prefs.playerBlur, 0.001f);
        assertEquals(0.42f, prefs.backdropDim, 0.001f);
        assertEquals(0.0f, prefs.meshSpeed, 0.001f);
        assertEquals(1.4f, prefs.meshIntensity, 0.001f);
        assertEquals(0.8f, prefs.gradientDepth, 0.001f);
        assertTrue(prefs.blendCovers);
        assertTrue(prefs.canvasOnCellular);
        assertFalse(prefs.artShrinkOnPause);
        assertFalse(prefs.vinylTonearm);
        assertTrue(prefs.hideStatusBarPlayer);
        assertFalse(prefs.playerHandle);
        assertTrue(prefs.playerNavBand);
    }

    @Test public void preservesVisiblePlayerDeckOrderAndVolumeVisibility() {
        PrismPlayerPreferences prismVolume = PrismPlayerPreferences.fromLyricsLook(
                "{\"playerPreferences\":{\"playerDeck\":[\"VOLUME\",\"STRIP\",\"TRANSPORT\",\"SCRUBBER\",\"BUTTONS\"],"
                        + "\"hiddenParts\":[\"TRANSPORT\"],\"hideVolumeBar\":true,\"volumeControl\":\"PRISM\"}}");
        assertEquals(Arrays.asList("STRIP", "SCRUBBER", "BUTTONS"),
                prismVolume.orderedDeckParts());

        PrismPlayerPreferences phoneVolume = PrismPlayerPreferences.fromLyricsLook(
                "{\"playerPreferences\":{\"playerDeck\":[\"STRIP\",\"SCRUBBER\",\"VOLUME\",\"TRANSPORT\"],"
                        + "\"hideVolumeBar\":true,\"volumeControl\":\"PHONE\"}}");
        assertEquals(Arrays.asList("STRIP", "SCRUBBER", "TRANSPORT"),
                phoneVolume.orderedDeckParts());
    }


    @Test public void alwaysKeepsLyricsBelowTheHeaderAndNeverAllocatesVolume() {
        PrismPlayerPreferences prefs = PrismPlayerPreferences.fromLyricsLook(
                "{\"playerPreferences\":{\"playerDeck\":[\"TRANSPORT\",\"VOLUME\",\"SCRUBBER\"],"
                        + "\"hideVolumeBar\":false,\"volumeControl\":\"PRISM\"}}");
        assertEquals(Arrays.asList("STRIP", "TRANSPORT", "SCRUBBER"), prefs.orderedDeckParts());

        PrismPlayerPreferences hiddenLyrics = PrismPlayerPreferences.fromLyricsLook(
                "{\"playerPreferences\":{\"playerDeck\":[\"VOLUME\",\"TRANSPORT\"],"
                        + "\"hiddenParts\":[\"LYRIC_LINE\"]}}");
        assertEquals(Arrays.asList("TRANSPORT"), hiddenLyrics.orderedDeckParts());
    }

    @Test public void volumeLevelUsesTheSelectedPrismOrPhoneVolumeSource() {
        PrismPlayerPreferences prismVolume = PrismPlayerPreferences.fromLyricsLook(
                "{\"playerPreferences\":{\"volumeControl\":\"PRISM\",\"appVolume\":0.37}}");
        assertEquals(0.37f, prismVolume.volumeLevel(0.92f), 0.001f);

        PrismPlayerPreferences phoneVolume = PrismPlayerPreferences.fromLyricsLook(
                "{\"playerPreferences\":{\"volumeControl\":\"PHONE\",\"appVolume\":0.22}}");
        assertEquals(0.92f, phoneVolume.volumeLevel(0.92f), 0.001f);
    }

    @Test public void prismOwnVolumeControlOverridesHideVolumeBarButNotHiddenDeckPart() {
        PrismPlayerPreferences prismVolume = PrismPlayerPreferences.fromLyricsLook(
                "{\"playerPreferences\":{\"playerDeck\":[\"VOLUME\"],"
                        + "\"hideVolumeBar\":true,\"volumeControl\":\"PRISM\"}}");
        assertTrue(prismVolume.showVolumeBar());

        PrismPlayerPreferences phoneVolume = PrismPlayerPreferences.fromLyricsLook(
                "{\"playerPreferences\":{\"playerDeck\":[\"VOLUME\"],"
                        + "\"hideVolumeBar\":true,\"volumeControl\":\"PHONE\"}}");
        assertFalse(phoneVolume.showVolumeBar());

        PrismPlayerPreferences hidden = PrismPlayerPreferences.fromLyricsLook(
                "{\"playerPreferences\":{\"playerDeck\":[\"VOLUME\"],"
                        + "\"hiddenParts\":[\"VOLUME\"],\"hideVolumeBar\":false,"
                        + "\"volumeControl\":\"PRISM\"}}");
        assertFalse(hidden.showVolumeBar());
    }

    @Test public void readsLegacyFlattenedFieldsAndResolvesAutoThumbByStyle() {
        PrismPlayerPreferences prefs = PrismPlayerPreferences.fromLyricsLook(
                "{\"playerTimes\":\"ELAPSED\",\"scrubberStyle\":\"RIBBONS\","
                        + "\"scrubberThumb\":\"AUTO\",\"playerTitleSize\":\"SMALL\"}");

        assertTrue(prefs.showElapsedTime());
        assertFalse(prefs.showRightTime());
        assertEquals("DOT", prefs.effectiveThumb());
        assertEquals(0.82f, prefs.titleScale(), 0.001f);
        assertEquals(400, prefs.titleFontWeight());
        assertEquals(400, prefs.artistFontWeight());
    }

    @Test public void defaultsAreSafeAndInvalidValuesAreIgnored() {
        PrismPlayerPreferences defaults = PrismPlayerPreferences.fromLyricsLook(null);
        assertTrue(defaults.showElapsedTime());
        assertTrue(defaults.showRightTime());
        assertTrue(defaults.showRemainingTime());
        assertEquals("NONE", defaults.effectiveThumb());
        assertEquals(20, defaults.artCornersDp);
        assertEquals("ROUNDED", defaults.coverShape);
        assertTrue(defaults.buttonsPill);
        assertTrue(defaults.playerNavBand);
        assertTrue(defaults.showsPart("LYRIC_LINE"));
        assertEquals(Arrays.asList("LYRICS", "VIDEO", "QUEUE"), defaults.playerButtons);

        PrismPlayerPreferences invalid = PrismPlayerPreferences.fromLyricsLook(
                "{\"playerPreferences\":{\"playerTimes\":\"BROKEN\","
                        + "\"scrubberThickness\":9,\"playerArtCorners\":-4}}");
        assertEquals(PrismPlayerPreferences.TIME_BOTH, invalid.playerTimes);
        assertEquals(2.0f, invalid.scrubberThickness, 0.001f);
        assertEquals(0, invalid.artCornersDp);
    }
}
