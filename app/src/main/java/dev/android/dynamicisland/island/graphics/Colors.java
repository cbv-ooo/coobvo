package dev.android.dynamicisland.island.graphics;

import android.graphics.Color;

/**
 * Material 3 Expressive colour roles for the island (0xAARRGGBB).
 * Solid opaque surfaces only — no frosted / translucent backgrounds on the runtime path.
 * Compact, expanded and incoming cards all resolve through these roles so the look stays one system.
 */
public final class Colors {
    public static final int BLACK = 0xFF000000;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int TRACK = 0x2EFFFFFF;
    public static final int TEXT_SECONDARY = 0x99FFFFFF;
    public static final int TEXT_BODY = 0xD9FFFFFF;
    public static final int TEXT_ARTIST = 0x8CFFFFFF;
    public static final int SEEK_FILL = 0xF2FFFFFF;
    public static final int SEEK_TRACK = 0x33FFFFFF;
    public static final int CONTROL_FILL = 0x24FFFFFF;
    public static final int BUTTON_ACTION_FILL = 0x32FFFFFF;
    public static final int CARD_STROKE = 0x40FFFFFF;
    public static final int PILL_OUTLINE = 0x47FFFFFF;
    public static final int RIM_TOP = 0x38FFFFFF;
    public static final int RIM_BOTTOM = 0x0FFFFFFF;
    public static final int CALL_GREEN = 0xFF34C759;
    public static final int BUTTON_GRAY = 0xFF3A3A3C;
    public static final int DESTRUCTIVE_RED = 0xFFFF3B30;
    public static final int CARD_GLASS = 0xFF0E0E10;
    public static final int CARD_SOLID = 0xFF141416;

    // —— M3E surface roles (opaque; shared by compact pill, bubbles, expanded cards) ——
    public static final int SURFACE = BLACK;
    /** Primary island fill — compact pill + expanded card body. */
    public static final int SURFACE_CONTAINER = 0xFF141418;
    /** Slightly lifted container for nested controls / secondary bubbles. */
    public static final int SURFACE_CONTAINER_HIGH = 0xFF1C1C22;
    public static final int SURFACE_CONTAINER_LOW = CARD_GLASS;
    /** Incoming / peek emphasis surface (a touch brighter than the resting pill). */
    public static final int SURFACE_BRIGHT = 0xFF1A1A20;
    public static final int OUTLINE = 0xFF8E9099;
    public static final int OUTLINE_VARIANT = 0xFF37373C;
    public static final int ON_SURFACE = WHITE;
    public static final int ON_SURFACE_VARIANT = 0xFFC6C5D0;
    public static final int ON_PRIMARY_CONTAINER = WHITE;
    public static final int PRIMARY = 0xFFA8C8FF;
    public static final int PRIMARY_CONTAINER = 0xFF00458E;
    public static final int SECONDARY_CONTAINER = 0xFF3E4759;
    public static final int ERROR = DESTRUCTIVE_RED;
    public static final int TRANSPARENT_MARK = 0x00FFFFFF;
    public static final int SEGMENT_UNREACHED = 0xFF9B9F9F;

    /** Default expanded-card corner radius in dp (M3E large). */
    public static final int CARD_CORNER_DP = 28;
    /** Compact pill end radius uses full height/2; this is the G2 power for corners. */
    public static final float CORNER_POWER_G2 = 2.4f;

    private static final int MAX_LIFT_PASSES = 12;
    private static final float MIN_LUMINANCE_ON_BLACK = 0.22f;
    private static final float LIFT_STEP = 0.12f;

    private Colors() {
    }

    /**
     * Builds an opaque primary container from a dynamic artwork accent. Darkening keeps the
     * accent recognisable while retaining a white on-container glyph across bright album colours.
     */
    public static int primaryContainer(int accent) {
        return blend(accent | BLACK, SURFACE, 0.56f) | BLACK;
    }

    /** Relative luminance (WCAG weights) of an sRGB colour, 0..1; alpha is ignored. */
    static float luminance(int color) {
        return 0.2126f * toLinear(Color.red(color))
                + 0.7152f * toLinear(Color.green(color))
                + 0.0722f * toLinear(Color.blue(color));
    }

    private static float toLinear(int channel) {
        float c = channel / 255.0f;
        if (c <= 0.04045f) {
            return c / 12.92f;
        }
        return (float) Math.pow((c + 0.055f) / 1.055f, 2.4f);
    }

    public static int readableOnBlack(int color) {
        int lifted = color | BLACK;
        for (int pass = 0; pass < MAX_LIFT_PASSES && luminance(lifted) < MIN_LUMINANCE_ON_BLACK; pass++) {
            lifted = blend(lifted, WHITE, LIFT_STEP);
        }
        return lifted;
    }

    public static int blend(int from, int to, float t) {
        float keep = 1.0f - t;
        return Color.argb(
                mix(Color.alpha(from), Color.alpha(to), keep, t),
                mix(Color.red(from), Color.red(to), keep, t),
                mix(Color.green(from), Color.green(to), keep, t),
                mix(Color.blue(from), Color.blue(to), keep, t));
    }

    private static int mix(int a, int b, float wa, float wb) {
        return Math.round((a * wa) + (b * wb));
    }

    public static int withAlpha(int color, float alpha) {
        float factor = Math.max(0.0f, Math.min(1.0f, alpha));
        int scaled = Math.round(Color.alpha(color) * factor);
        return (color & 0x00FFFFFF) | (scaled << 24);
    }
}
