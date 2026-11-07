package dev.android.dynamicisland.island;

import static org.junit.Assert.assertEquals;

import dev.android.dynamicisland.settings.ModuleSettings;
import org.junit.Test;

public class IslandPlacementTest {
    @Test
    public void rightOfCentrePillPlacesCompanionsToTheLeftInAutoMode() {
        assertEquals(-1, IslandPlacement.bubbleSideForCenter(800.0f, 1080.0f, ModuleSettings.ANCHOR_AUTO));
    }

    @Test
    public void leftOfCentrePillPlacesCompanionsToTheRight() {
        assertEquals(1, IslandPlacement.bubbleSideForCenter(280.0f, 1080.0f, ModuleSettings.ANCHOR_AUTO));
    }

    @Test
    public void anchorIsOnlyUsedAsATieBreakerWhenCentred() {
        assertEquals(1, IslandPlacement.bubbleSideForCenter(540.0f, 1080.0f, ModuleSettings.ANCHOR_AUTO));
        assertEquals(-1, IslandPlacement.bubbleSideForCenter(540.0f, 1080.0f, ModuleSettings.ANCHOR_RIGHT));
    }

    @Test
    public void smallCutoutUsesItsActualDimensionsWithoutACentrePositionCheck() {
        assertEquals(31.2f, IslandPlacement.visibleCameraHoleDiameter(40, 40), 0.01f);
        assertEquals(0.0f, IslandPlacement.visibleCameraHoleDiameter(0, 40), 0.0f);
    }
}
