package dev.android.dynamicisland.island.view.card;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MediaBodyProgressTest {
    @Test public void crossfadeProgressMovesInsideTheReportedMixWindow() {
        assertEquals(0.30f, MediaBody.crossfadeProgressFraction(30_000L, 10_000L, 0.0f, 100_000L), 0.001f);
        assertEquals(0.35f, MediaBody.crossfadeProgressFraction(30_000L, 10_000L, 0.5f, 100_000L), 0.001f);
        assertEquals(0.40f, MediaBody.crossfadeProgressFraction(30_000L, 10_000L, 1.0f, 100_000L), 0.001f);
    }

    @Test public void invalidCrossfadeWindowDoesNotCreateAProgressMarker() {
        assertEquals(0.0f, MediaBody.crossfadeProgressFraction(-1L, 10_000L, 0.5f, 100_000L), 0.001f);
        assertEquals(0.0f, MediaBody.crossfadeProgressFraction(30_000L, 0L, 0.5f, 100_000L), 0.001f);
        assertEquals(0.0f, MediaBody.crossfadeProgressFraction(30_000L, 10_000L, 0.5f, 0L), 0.001f);
    }
}
