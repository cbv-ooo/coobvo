package dev.android.dynamicisland.island.view;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.View;

/** Compatibility shim: transitions remain crisp and never apply RenderEffect. */
final class ContentBlur {
    static final float RADIUS_DP = 0;

    Canvas begin(Canvas target, RectF bounds, float amount, float radius) { return target; }
    void end(Canvas target, Canvas recording) { }
    void apply(View view, float amount, float radius) {
        if (view != null) view.setRenderEffect(null);
    }
    void clear() { }
    void clearView() { }
}
