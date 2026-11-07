package dev.android.dynamicisland.island.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import android.graphics.Canvas;
import android.graphics.RectF;
import org.junit.Test;

/** Regression coverage for the deliberate removal of foreground transition blur. */
public class BlurGovernorTest {
    @Test public void transitionContentUsesTheOriginalCanvasWithoutFiltering() {
        ContentBlur blur = new ContentBlur();
        Canvas target = new Canvas();
        Canvas result = blur.begin(target, new RectF(0, 0, 100, 40), 1f, 24f);
        assertSame("no RenderNode recording or blurred intermediate is created", target, result);
        assertEquals("the blur radius is permanently disabled", 0f, ContentBlur.RADIUS_DP, 0f);
        blur.end(target, result);
    }
}
