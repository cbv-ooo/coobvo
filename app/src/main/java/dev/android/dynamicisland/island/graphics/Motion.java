package dev.android.dynamicisland.island.graphics;

/**
 * Material 3 Expressive motion tokens for the island's Canvas animations.
 *
 * <p>{@code response} is the spring period in seconds and {@code damping} the damping ratio.
 * Spatial springs may overshoot; effects springs are critically damped so colour and opacity
 * settle without ringing. The response values are derived from the Material stiffness tokens
 * with a unit mass: {@code response = 2 * PI / sqrt(stiffness)}.
 */
public final class Motion {
    /** Small spatial changes such as presses, chips and bubble nudges. */
    public static final Motion SPATIAL_FAST = new Motion(0.22f, 0.6f);
    /** Pill and card size/position changes. */
    public static final Motion SPATIAL_DEFAULT = new Motion(0.32f, 0.8f);
    /** Large container changes. */
    public static final Motion SPATIAL_SLOW = new Motion(0.44f, 0.8f);

    /** Fast colour and opacity changes. */
    public static final Motion EFFECTS_FAST = new Motion(0.10f, 1.0f);
    /** Standard cross-fades. */
    public static final Motion EFFECTS_DEFAULT = new Motion(0.16f, 1.0f);
    /** Slow colour and opacity changes. */
    public static final Motion EFFECTS_SLOW = new Motion(0.22f, 1.0f);

    // Named island transitions map onto the shared tokens so related movements stay consistent.
    public static final Motion PILL_GROW = SPATIAL_DEFAULT;
    public static final Motion PILL_DROP = SPATIAL_SLOW;
    public static final Motion PILL_RESIZE = SPATIAL_DEFAULT;
    public static final Motion PILL_SHRINK = SPATIAL_DEFAULT;
    public static final Motion PILL_GATHER = SPATIAL_SLOW;
    public static final Motion PILL_REGROW = SPATIAL_SLOW;

    public static final Motion CARD_DROP = SPATIAL_SLOW;
    public static final Motion CARD_WIDEN = SPATIAL_DEFAULT;
    public static final Motion CARD_GROW = SPATIAL_SLOW;
    public static final Motion CARD_RETURN = SPATIAL_DEFAULT;
    public static final Motion CARD_FOLD = SPATIAL_DEFAULT;

    public static final Motion BUBBLE_SPLIT = SPATIAL_FAST;
    public static final Motion BUBBLE_MERGE = SPATIAL_FAST;

    public static final Motion RUBBER_RELEASE = SPATIAL_DEFAULT;
    public static final Motion PRESS_DOWN = SPATIAL_FAST;
    public static final Motion PRESS_UP = SPATIAL_FAST;

    public final float response;
    public final float damping;

    private Motion(float response, float damping) {
        this.response = response;
        this.damping = damping;
    }

    /** The same spring, played {@code factor} times as fast. */
    public Motion faster(float factor) {
        return new Motion(response / factor, damping);
    }
}
