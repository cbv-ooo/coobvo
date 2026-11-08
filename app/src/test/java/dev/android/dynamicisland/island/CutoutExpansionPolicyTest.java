package dev.android.dynamicisland.island;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import dev.android.dynamicisland.island.view.CutoutExpansionPolicy;
import org.junit.Test;

public class CutoutExpansionPolicyTest {
    @Test public void keepsAutomaticPeekCompactWhenItsExpansionSweepCrossesTheCutout() {
        assertTrue(CutoutExpansionPolicy.overlaps(160, 10, 340, 40, 330, 5, 350, 25));
    }

    @Test public void aConfiguredNarrowCardCanStayClearOfTheCutout() {
        assertFalse(CutoutExpansionPolicy.overlaps(220, 10, 320, 40, 330, 5, 350, 25));
    }

    @Test public void ignoresCutoutsOutsideTheExpansionSweep() {
        assertFalse(CutoutExpansionPolicy.overlaps(160, 40, 340, 90, 330, 5, 350, 25));
    }
}
