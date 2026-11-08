package dev.android.dynamicisland.settings;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewParent;
import android.widget.Switch;

/**
 * Material 3 Expressive switch (Track B): taller track, larger thumb, primary-tinted on state.
 * Native Switch touch, RTL and accessibility stay intact.
 */
final class GalaxySwitch extends Switch {
    private final float density;
    private final int onColor;
    private final int offColor;

    GalaxySwitch(Context context) {
        this(context, 0);
    }

    /** {@code accent} 0 uses the default M3 primary; otherwise the module accent. */
    GalaxySwitch(Context context, int accent) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        onColor = accent != 0 ? (accent | 0xFF000000) : (dark ? 0xFFA8C8FF : 0xFF0058CA);
        offColor = dark ? 0xFF636368 : 0xFF8F8F94;
        setShowText(false);
        setSplitTrack(false);
        setSwitchMinWidth(dp(52));
        setSwitchPadding(0);
        setThumbTextPadding(0);
        setThumbTintList(null);
        setTrackTintList(null);
        setTrackDrawable(new Track());
        setThumbDrawable(new Thumb());
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(4), 0, dp(4), 0);
        setMinWidth(dp(48));
        setMinHeight(dp(48));
    }

    private int dp(float value) { return Math.round(value * density); }

    private float disabledOpacity() {
        if (isEnabled()) return 1;
        if (getAlpha() < 1) return 1;
        ViewParent parent = getParent();
        while (parent instanceof View) {
            View view = (View) parent;
            if (view.getAlpha() < 1) return 1;
            parent = view.getParent();
        }
        return .4f;
    }

    private abstract class Part extends Drawable {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int alpha = 255;
        @Override public void setAlpha(int value) { alpha = value; invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter value) { paint.setColorFilter(value); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override public boolean isStateful() { return true; }
        @Override protected boolean onStateChange(int[] state) { invalidateSelf(); return true; }
        void colour(int value) {
            paint.setColor(value);
            paint.setAlpha(Math.round(alpha * disabledOpacity()));
        }
    }

    private final class Track extends Part {
        @Override public int getIntrinsicWidth() { return dp(52); }
        @Override public int getIntrinsicHeight() { return dp(32); }
        @Override public boolean getPadding(Rect padding) { padding.set(dp(4), 0, dp(4), 0); return true; }
        @Override public void draw(Canvas canvas) {
            colour(isChecked() ? onColor : offColor);
            Rect r = getBounds();
            float radius = r.height() / 2f;
            canvas.drawRoundRect(r.left, r.top, r.right, r.bottom, radius, radius, paint);
        }
    }

    private final class Thumb extends Part {
        @Override public int getIntrinsicWidth() { return dp(24); }
        @Override public int getIntrinsicHeight() { return dp(32); }
        @Override public void draw(Canvas canvas) {
            colour(0xFFFAFAFA);
            Rect r = getBounds();
            float radius = Math.min(dp(12), Math.min(r.width(), r.height()) / 2f);
            canvas.drawCircle(r.exactCenterX(), r.exactCenterY(), radius, paint);
        }
    }
}
